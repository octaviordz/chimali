import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import {
  generateAuthenticationOptions,
  generateRegistrationOptions,
  verifyAuthenticationResponse,
  verifyRegistrationResponse,
} from '@simplewebauthn/server';

const host = '127.0.0.1';
const port = Number(process.env.PORT ?? 4173);
const rpID = 'localhost';
const origin = `http://${rpID}:${port}`;
const rpName = 'Chimali Disposable RP';
const account = {
  id: randomBytes(16),
  name: 'chimali-disposable-test-user',
  credential: undefined,
  registrationChallenge: undefined,
  authenticationChallenge: undefined,
};
const performanceSamples = [];

const json = (res, status, body) => {
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
  });
  res.end(JSON.stringify(body));
};

async function bodyJson(req) {
  let text = '';
  for await (const chunk of req) {
    text += chunk;
    if (text.length > 1_000_000) throw new Error('Request body exceeds 1 MB');
  }
  return JSON.parse(text);
}

async function handle(req, res) {
  const path = new URL(req.url ?? '/', origin).pathname;
  try {
    if (req.method === 'GET' && path === '/') {
      const html = await readFile(new URL('./index.html', import.meta.url));
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
      res.end(html);
      return;
    }

    if (req.method === 'GET' && path === '/api/perf/results') {
      json(res, 200, { samples: performanceSamples });
      return;
    }

    if (req.method === 'POST' && path === '/api/perf/reset') {
      performanceSamples.length = 0;
      json(res, 200, { cleared: true });
      return;
    }

    if (req.method === 'POST' && path === '/api/perf/record') {
      const sample = await bodyJson(req);
      const { sampleId, ceremony, totalMs, userVerificationMs, appOperationIds, verified } = sample;
      if (typeof sampleId !== 'string' || !/^[a-f0-9-]{16,64}$/iu.test(sampleId)) {
        throw new Error('A valid sample ID is required');
      }
      if (!['registration', 'authentication'].includes(ceremony)) {
        throw new Error('Unsupported ceremony type');
      }
      if (verified !== true) throw new Error('Only RP-verified ceremonies can be recorded');
      if (!Number.isFinite(totalMs) || totalMs < 0 || !Number.isFinite(userVerificationMs) || userVerificationMs < 0 || userVerificationMs > totalMs) {
        throw new Error('Invalid total or user-verification duration');
      }
      if (!Array.isArray(appOperationIds) || appOperationIds.length === 0 || appOperationIds.some((id) => !Number.isInteger(id) || id < 1)) {
        throw new Error('Correlated Chimali operation IDs are required');
      }
      if (performanceSamples.some((entry) => entry.sampleId === sampleId)) {
        throw new Error('Sample ID has already been recorded');
      }
      const adjustedEndToEndMs = Math.max(0, totalMs - userVerificationMs);
      const record = {
        sampleId,
        ceremony,
        totalMs: Math.round(totalMs * 100) / 100,
        userVerificationMs: Math.round(userVerificationMs * 100) / 100,
        adjustedEndToEndMs: Math.round(adjustedEndToEndMs * 100) / 100,
        appOperationIds,
        verified,
        withinBudget: adjustedEndToEndMs < 1_000,
      };
      performanceSamples.push(record);
      console.log(`SC007_E2E ${JSON.stringify(record)}`);
      json(res, 200, { recorded: true, sample: record, count: performanceSamples.length });
      return;
    }

    if (req.method === 'POST' && path === '/api/register/options') {
      const options = await generateRegistrationOptions({
        rpName,
        rpID,
        userID: account.id,
        userName: account.name,
        attestationType: 'none',
        supportedAlgorithmIDs: [-7],
        authenticatorSelection: {
          authenticatorAttachment: 'cross-platform',
          residentKey: 'discouraged',
          userVerification: 'required',
        },
        excludeCredentials: account.credential ? [{
          id: account.credential.id,
          transports: account.credential.transports,
        }] : [],
      });
      account.registrationChallenge = options.challenge;
      json(res, 200, options);
      return;
    }

    if (req.method === 'POST' && path === '/api/register/verify') {
      const response = await bodyJson(req);
      if (!account.registrationChallenge) throw new Error('No registration challenge is pending');
      const verification = await verifyRegistrationResponse({
        response,
        expectedChallenge: account.registrationChallenge,
        expectedOrigin: origin,
        expectedRPID: rpID,
        requireUserVerification: false,
      });
      account.registrationChallenge = undefined;
      if (!verification.verified || !verification.registrationInfo) {
        json(res, 400, { verified: false, error: 'Registration verification failed' });
        return;
      }
      const { credential, credentialDeviceType, credentialBackedUp } = verification.registrationInfo;
      account.credential = {
        id: credential.id,
        publicKey: credential.publicKey,
        counter: credential.counter,
        transports: response.response.transports ?? [],
        credentialDeviceType,
        credentialBackedUp,
      };
      json(res, 200, {
        verified: true,
        credentialId: credential.id,
        counter: credential.counter,
        credentialDeviceType,
        credentialBackedUp,
      });
      return;
    }

    if (req.method === 'POST' && path === '/api/auth/options') {
      if (!account.credential) throw new Error('Register the synthetic test credential first');
      const options = await generateAuthenticationOptions({
        rpID,
        userVerification: 'required',
        allowCredentials: [{
          id: account.credential.id,
          transports: account.credential.transports,
        }],
      });
      account.authenticationChallenge = options.challenge;
      json(res, 200, options);
      return;
    }

    if (req.method === 'POST' && path === '/api/auth/verify') {
      const response = await bodyJson(req);
      const credential = account.credential;
      if (!credential || !account.authenticationChallenge) {
        throw new Error('No registered credential or authentication challenge is available');
      }
      if (response.id !== credential.id) throw new Error('Assertion credential ID does not match');
      const verification = await verifyAuthenticationResponse({
        response,
        expectedChallenge: account.authenticationChallenge,
        expectedOrigin: origin,
        expectedRPID: rpID,
        credential: {
          id: credential.id,
          publicKey: credential.publicKey,
          counter: credential.counter,
          transports: credential.transports,
        },
        requireUserVerification: false,
      });
      account.authenticationChallenge = undefined;
      if (!verification.verified) {
        json(res, 400, { verified: false, error: 'Assertion verification failed' });
        return;
      }
      const previousCounter = credential.counter;
      credential.counter = verification.authenticationInfo.newCounter;
      json(res, 200, {
        verified: true,
        credentialId: credential.id,
        previousCounter,
        counter: credential.counter,
        userVerified: verification.authenticationInfo.userVerified,
      });
      return;
    }

    json(res, 404, { error: 'Not found' });
  } catch (error) {
    json(res, 400, { verified: false, error: error instanceof Error ? error.message : String(error) });
  }
}

createServer((req, res) => void handle(req, res)).listen(port, host, () => {
  console.log(`Disposable RP listening at ${origin}`);
  console.log('Synthetic credential and challenge state is memory-only and will be cleared on exit.');
});
