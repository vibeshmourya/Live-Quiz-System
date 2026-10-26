const http = require('http');

function validateAndResolve(input, sessionPin) {
  if (!input || !input.trim()) {
    return { isValid: false, fullUrl: '', error: 'Join URL cannot be empty.' };
  }
  let raw = input.trim();
  if (!/^https?:\/\//i.test(raw)) {
    raw = 'https://' + raw;
  }
  try {
    const parsed = new URL(raw);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return { isValid: false, fullUrl: '', error: 'Only HTTP and HTTPS URLs are supported.' };
    }
    if (!parsed.hostname || (!parsed.hostname.includes('.') && parsed.hostname !== 'localhost')) {
      return { isValid: false, fullUrl: '', error: 'Please enter a valid domain or public URL (e.g. ngrok).' };
    }
    const origin = parsed.origin;
    const fullUrl = `${origin}/join?pin=${sessionPin}`;
    return { isValid: true, fullUrl, origin, error: '' };
  } catch (e) {
    return { isValid: false, fullUrl: '', error: 'Invalid URL format.' };
  }
}

function request(options, data) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        try {
          const parsed = body ? JSON.parse(body) : {};
          resolve({ status: res.statusCode, headers: res.headers, body: parsed });
        } catch (e) {
          resolve({ status: res.statusCode, headers: res.headers, raw: body });
        }
      });
    });
    req.on('error', reject);
    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

async function runTests() {
  console.log('====================================================');
  console.log('🧪 DIRECT JOIN URL & QR CODE RESOLUTION TESTS');
  console.log('====================================================\n');

  let passed = 0;
  let failed = 0;

  function assert(condition, message) {
    if (condition) {
      console.log(`  ✓ ${message}`);
      passed++;
    } else {
      console.error(`  ✕ FAILED: ${message}`);
      failed++;
    }
  }

  const pin = '849201';

  // 1. Base ngrok URL
  console.log('--- Test 1: Base ngrok URL input ---');
  const res1 = validateAndResolve('https://abcdef123.ngrok-free.app', pin);
  assert(res1.isValid === true, `Base ngrok URL is valid`);
  assert(res1.fullUrl === 'https://abcdef123.ngrok-free.app/join?pin=849201', `Resolves to full join URL with PIN`);
  assert(res1.origin === 'https://abcdef123.ngrok-free.app', `Origin extracted properly`);

  // 2. Trailing slash
  console.log('\n--- Test 2: URL with trailing slash ---');
  const res2 = validateAndResolve('https://abcdef123.ngrok-free.app/', pin);
  assert(res2.isValid === true, `URL with trailing slash is valid`);
  assert(res2.fullUrl === 'https://abcdef123.ngrok-free.app/join?pin=849201', `Trailing slash stripped before /join`);

  // 3. User pastes bare hostname without https://
  console.log('\n--- Test 3: Bare domain without protocol ---');
  const res3 = validateAndResolve('my-tunnel.ngrok.io', pin);
  assert(res3.isValid === true, `Bare domain parsed successfully`);
  assert(res3.fullUrl === 'https://my-tunnel.ngrok.io/join?pin=849201', `Prepends https:// and appends join route`);

  // 4. User pastes full URL with an arbitrary path (security / routing check)
  console.log('\n--- Test 4: Arbitrary path normalization ---');
  const res4 = validateAndResolve('https://abcdef123.ngrok-free.app/some/random/dashboard', pin);
  assert(res4.isValid === true, `Valid URL`);
  assert(res4.fullUrl === 'https://abcdef123.ngrok-free.app/join?pin=849201', `Arbitrary path replaced with expected /join?pin=${pin}`);

  // 5. User pastes URL with old or different PIN
  console.log('\n--- Test 5: Old PIN replacement with active PIN ---');
  const res5 = validateAndResolve('https://abcdef123.ngrok-free.app/join?pin=000000', pin);
  assert(res5.isValid === true, `Valid URL`);
  assert(res5.fullUrl === 'https://abcdef123.ngrok-free.app/join?pin=849201', `Active PIN ${pin} replaces stale PIN`);

  // 6. Localhost URL
  console.log('\n--- Test 6: Localhost URL ---');
  const res6 = validateAndResolve('http://localhost:5173', pin);
  assert(res6.isValid === true, `Localhost URL is valid`);
  assert(res6.fullUrl === 'http://localhost:5173/join?pin=849201', `Resolves to localhost join URL`);

  // 7. Invalid URL rejection
  console.log('\n--- Test 7: Malformed / Invalid URLs ---');
  const inv1 = validateAndResolve('', pin);
  assert(inv1.isValid === false, `Empty URL is invalid (Message: "${inv1.error}")`);

  const inv2 = validateAndResolve('javascript:alert(1)', pin);
  assert(inv2.isValid === false, `javascript: protocol is rejected (Message: "${inv2.error}")`);

  const inv3 = validateAndResolve('not_a_valid_domain', pin);
  assert(inv3.isValid === false, `Single-word host without dot is rejected (Message: "${inv3.error}")`);

  // 8. End-to-end integration test with student join flow via API
  console.log('\n--- Test 8: Student Join flow with direct join URL ---');
  const facultyLogin = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/login',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { username: 'faculty', password: 'password123' });
  const facultyToken = facultyLogin.body.token;

  // Create session
  const quizRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/quizzes',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, {
    title: 'QR Code Direct Join Verification Exam',
    examStatus: 'SCHEDULED',
    answerChangePolicy: 'ALLOW_RESELECTION',
    questions: [{
      questionText: 'Test question for direct join URL',
      timeLimit: 30,
      marks: 10,
      options: [
        { optionLabel: 'A', optionText: 'Option 1', isCorrect: true },
        { optionLabel: 'B', optionText: 'Option 2', isCorrect: false }
      ]
    }]
  });

  const sessionRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/create',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, { quizId: quizRes.body.id });
  const activePin = sessionRes.body.pin;

  // Resolve simulated ngrok URL for this session
  const ngrokJoin = validateAndResolve('https://my-ngrok-test.ngrok-free.app', activePin);
  assert(ngrokJoin.fullUrl === `https://my-ngrok-test.ngrok-free.app/join?pin=${activePin}`, `QR Code encodes: ${ngrokJoin.fullUrl}`);

  // Candidate accesses /join?pin=${activePin}
  const candJoin = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/join',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    sessionPin: activePin,
    nickname: 'Mobile Candidate',
    candidateId: 'cand_qr_1'
  });
  assert(candJoin.status === 200, `Student successfully joined session via PIN ${activePin}`);

  // Check host state
  const hostState = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${activePin}/host`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });
  assert(hostState.body.participantCount >= 1, `Host lobby reflects participant count: ${hostState.body.participantCount}`);

  console.log('\n====================================================');
  console.log(`📊 RESULTS: ${passed} PASSED, ${failed} FAILED`);
  console.log('====================================================');

  if (failed > 0) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error('Test run error:', err);
  process.exit(1);
});
