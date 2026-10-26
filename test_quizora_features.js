const http = require('http');

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
  console.log('🧪 QUIZORA COMPREHENSIVE AUTOMATED VERIFICATION');
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

  // 1. Faculty Login with Default Credentials
  console.log('--- Test 1: Default Faculty Login ---');
  const defaultLogin = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/login',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { username: 'faculty', password: 'password123' });

  assert(defaultLogin.status === 200, `Default faculty login returned 200 (Got ${defaultLogin.status})`);
  assert(defaultLogin.body.token != null, `Received auth token: ${defaultLogin.body.token?.substring(0, 10)}...`);

  // 2. Faculty Registration
  console.log('\n--- Test 2: Faculty Registration ---');
  const testEmail = `prof_${Date.now()}@quizora.edu`;
  const regRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/register',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    name: 'Prof. Ada Lovelace',
    email: testEmail,
    password: 'securePassword456'
  });

  assert(regRes.status === 200, `Faculty registration returned 200 (Got ${regRes.status})`);
  assert(regRes.body.email === testEmail, `Registered email returned correctly: ${regRes.body.email}`);
  assert(regRes.body.name === 'Prof. Ada Lovelace', `Registered name matches: ${regRes.body.name}`);
  assert(regRes.body.token != null, `Auto-login token provided upon registration`);

  // 3. Duplicate Email Prevention
  console.log('\n--- Test 3: Duplicate Email Prevention ---');
  const dupRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/register',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    name: 'Prof. Imposter',
    email: testEmail,
    password: 'differentPassword789'
  });

  assert(dupRes.status === 409, `Duplicate email registration rejected with 409 Conflict (Got ${dupRes.status})`);

  // 4. Login with newly registered Faculty Email
  console.log('\n--- Test 4: Login with Registered Email ---');
  const emailLogin = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/login',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { email: testEmail, password: 'securePassword456' });

  assert(emailLogin.status === 200, `Email login returned 200 (Got ${emailLogin.status})`);
  const facultyToken = emailLogin.body.token;

  // 5. Check Profile endpoint
  const profileRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/me',
    method: 'GET',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });
  assert(profileRes.status === 200, `/api/auth/me returned 200`);
  assert(profileRes.body.email === testEmail, `Profile email verified: ${profileRes.body.email}`);

  // 6. Test Single Selection Answer Policy
  console.log('\n--- Test 5: Single Selection Answer Policy ---');
  const singleQuizRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/quizzes',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, {
    title: 'Single Selection Verification Exam',
    examStatus: 'SCHEDULED',
    answerChangePolicy: 'SINGLE_SELECTION',
    questions: [{
      questionText: 'What is 2 + 2?',
      timeLimit: 30,
      marks: 10,
      options: [
        { optionLabel: 'A', optionText: '3', isCorrect: false },
        { optionLabel: 'B', optionText: '4', isCorrect: true },
        { optionLabel: 'C', optionText: '5', isCorrect: false },
        { optionLabel: 'D', optionText: '6', isCorrect: false }
      ]
    }]
  });
  assert(singleQuizRes.status === 201 || singleQuizRes.status === 200, `Created quiz with SINGLE_SELECTION policy`);
  assert(singleQuizRes.body.answerChangePolicy === 'SINGLE_SELECTION', `Quiz policy is SINGLE_SELECTION`);

  // Start live session
  const singleSessionRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/create',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, { quizId: singleQuizRes.body.id });
  const singlePin = singleSessionRes.body.pin;
  assert(singleSessionRes.status === 201 || singleSessionRes.status === 200, `Created live session with PIN: ${singlePin}`);

  // Candidate joins
  const candJoinRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/join',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    sessionPin: singlePin,
    nickname: 'Student A',
    candidateId: 'cand_single_1'
  });
  assert(candJoinRes.status === 200, `Candidate joined session`);

  // Start Question 0
  const startQRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${singlePin}/start-question`,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, { questionIndex: 0 });
  assert(startQRes.status === 200, `Question started by host`);

  // Verify candidate session state shows allowReselection = false
  const candStateRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${singlePin}/candidate?candidateId=cand_single_1`,
    method: 'GET'
  });
  assert(candStateRes.status === 200, `Fetched candidate state`);
  assert(candStateRes.body.allowReselection === false, `Candidate state reports allowReselection = false`);
  const singleQId = candStateRes.body.currentQuestion.id;

  // First submission: Option A
  const sub1 = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${singlePin}/submit`,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    candidateId: 'cand_single_1',
    questionId: singleQId,
    optionLabel: 'A'
  });
  assert(sub1.status === 200, `First answer submission accepted for SINGLE_SELECTION`);

  // Second submission attempt: Option B (Must be REJECTED)
  const sub2 = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${singlePin}/submit`,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, {
    candidateId: 'cand_single_1',
    questionId: singleQId,
    optionLabel: 'B'
  });
  assert(sub2.status !== 200, `Second submission attempt was properly REJECTED (Status: ${sub2.status}, Error: ${sub2.body.error})`);

  // 7. Test Allow Reselection Answer Policy
  console.log('\n--- Test 6: Allow Reselection Answer Policy ---');
  const reselectQuizRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/quizzes',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, {
    title: 'Allow Reselection Verification Exam',
    examStatus: 'SCHEDULED',
    answerChangePolicy: 'ALLOW_RESELECTION',
    questions: [{
      questionText: 'Which planet is known as the Red Planet?',
      timeLimit: 30,
      marks: 10,
      options: [
        { optionLabel: 'A', optionText: 'Venus', isCorrect: false },
        { optionLabel: 'B', optionText: 'Mars', isCorrect: true },
        { optionLabel: 'C', optionText: 'Jupiter', isCorrect: false },
        { optionLabel: 'D', optionText: 'Mercury', isCorrect: false }
      ]
    }]
  });
  assert(reselectQuizRes.status === 201 || reselectQuizRes.status === 200, `Created quiz with ALLOW_RESELECTION policy`);
  assert(reselectQuizRes.body.answerChangePolicy === 'ALLOW_RESELECTION', `Quiz policy is ALLOW_RESELECTION`);

  // Start live session
  const reselectSessionRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/create',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, { quizId: reselectQuizRes.body.id });
  const reselectPin = reselectSessionRes.body.pin;
  assert(reselectSessionRes.status === 201 || reselectSessionRes.status === 200, `Created live session with PIN: ${reselectPin}`);

  // Join Candidate 1 and Candidate 2
  await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/join',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { sessionPin: reselectPin, nickname: 'Alice', candidateId: 'cand_res_1' });

  await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/sessions/join',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { sessionPin: reselectPin, nickname: 'Bob', candidateId: 'cand_res_2' });

  // Start question
  await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/start-question`,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${facultyToken}`
    }
  }, { questionIndex: 0 });

  // Verify candidate state reports allowReselection = true
  const candResState = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/candidate?candidateId=cand_res_1`,
    method: 'GET'
  });
  assert(candResState.body.allowReselection === true, `Candidate state reports allowReselection = true`);
  const qId = candResState.body.currentQuestion.id;

  // Alice submits A (Wrong)
  const aSub1 = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/submit`,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { candidateId: 'cand_res_1', questionId: qId, optionLabel: 'A' });
  assert(aSub1.status === 200, `Alice initial submission (A) accepted`);

  // Bob submits B (Correct)
  const bSub1 = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/submit`,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { candidateId: 'cand_res_2', questionId: qId, optionLabel: 'B' });
  assert(bSub1.status === 200, `Bob initial submission (B) accepted`);

  // Check host state stats: totalSubmissions = 2, A: 1, B: 1
  let hostState = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/host`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });
  assert(hostState.body.submissionCount === 2, `Total submissions before change: 2`);
  assert(hostState.body.optionCounts && hostState.body.optionCounts.A === 1, `Counts before change: A=1 (Got ${hostState.body.optionCounts?.A})`);
  assert(hostState.body.optionCounts && hostState.body.optionCounts.B === 1, `Counts before change: B=1 (Got ${hostState.body.optionCounts?.B})`);

  // Alice RESELECTS her answer: changes from A to B (Mars - Correct)!
  const aSub2 = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/submit`,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  }, { candidateId: 'cand_res_1', questionId: qId, optionLabel: 'B' });
  assert(aSub2.status === 200, `Alice reselected answer (changed to B) accepted`);
  assert(aSub2.body.submittedLabel === 'B', `Updated submission reflects Option B`);

  // Check host state stats after Alice's change: totalSubmissions must STILL be 2 (NO DUPLICATES), A: 0, B: 2!
  hostState = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/host`,
    method: 'GET',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });
  assert(hostState.body.submissionCount === 2, `Total submissions remains exactly 2 (no duplicate candidate rows)`);
  assert(hostState.body.optionCounts && hostState.body.optionCounts.A === 0, `Counts accurately updated: Option A = 0`);
  assert(hostState.body.optionCounts && hostState.body.optionCounts.B === 2, `Counts accurately updated: Option B = 2`);

  // Reveal Answer & Check Scores
  console.log('\n--- Test 7: Reveal & Final Scoring ---');
  await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/reveal`,
    method: 'POST',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });

  // End session
  await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/end`,
    method: 'POST',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });

  // Candidate 1 final state: score must include the 10 marks from the reselected correct answer!
  const aliceFinal = await request({
    hostname: 'localhost',
    port: 8080,
    path: `/api/sessions/${reselectPin}/candidate?candidateId=cand_res_1`,
    method: 'GET'
  });
  assert(aliceFinal.body.myScore === 10, `Alice final score accurately reflects reselected answer: 10 marks (Got ${aliceFinal.body.myScore})`);
  assert(aliceFinal.body.mySelectedLabel === 'B', `Alice final selected option is B`);
  assert(aliceFinal.body.wasCorrect === true, `Alice marked as correct`);

  // 8. Logout Test
  console.log('\n--- Test 8: Faculty Logout ---');
  const logoutRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/logout',
    method: 'POST',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });
  assert(logoutRes.status === 200, `Logout endpoint returned 200`);

  const expiredRes = await request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/me',
    method: 'GET',
    headers: { 'Authorization': `Bearer ${facultyToken}` }
  });
  assert(expiredRes.status === 401, `Token invalidated on backend after logout (Status: ${expiredRes.status})`);

  console.log('\n====================================================');
  console.log(`📊 RESULTS: ${passed} PASSED, ${failed} FAILED`);
  console.log('====================================================');

  if (failed > 0) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
