// Mock external systems for the Examination Revaluation process (final phase).
//
// These pretend to be the systems a university already has. The process talks to them
// exactly the way it would talk to the real ones:
//
//   GET   /results/:registerNo-:courseCode   Results system (REST connector "Fetch published result")
//   PATCH /results/:registerNo-:courseCode   Results system (REST connector "Publish result")
//   POST  /letters                           Document store (Spring Boot job worker "generate-letter")
//   /razorpay/v1/...                         Razorpay-compatible payment API (REST connectors
//                                            "Create fee payment link" and "Refund revaluation fee")
//   /pay/:paymentLinkId                      Test checkout page: this is where the student "pays"
//   /                                        Dashboard for the demo, with switches that break a system
//
// Since the final phase, examiners live in the backend database and emails go out through the
// Camunda Email connector to Mailpit, so the examiner and email routes of Review 2 were removed.
//
// Run:  node mock-services.js       (Node 18+, nothing to install)
// In Docker, PUBLIC_URL is the address the *browser* uses (http://localhost:3000), because the
// payment link's short_url is opened by a person, not by a container.
// Then open http://localhost:3000

const http = require('node:http');
const { randomBytes } = require('node:crypto');

const PORT = Number(process.env.SERVICES_PORT || 3000);
const BASE = process.env.PUBLIC_URL || `http://localhost:${PORT}`;

const newId = (prefix) => `${prefix}_${randomBytes(7).toString('hex')}`;
const nowSec = () => Math.floor(Date.now() / 1000);
const dateDaysAgo = (n) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  const p = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

// Published results. Any register number works; the course code decides what comes back.
// The first four are the same test courses as Phase 1.
const COURSES = {
  '21CSC305P': { courseName: 'Machine Learning', courseType: 'THEORY', marks: 68, grade: 'B+', publishedDaysAgo: 2, examinerId: 'EXM-101' },
  '21CSC303J': { courseName: 'Operating Systems', courseType: 'LAB_INTEGRATED_THEORY', marks: 47, grade: 'F', publishedDaysAgo: 1, examinerId: 'EXM-104', publishFails: true },
  '21CSC306L': { courseName: 'Machine Learning Lab', courseType: 'PRACTICAL', marks: 40, grade: 'F', publishedDaysAgo: 2, examinerId: 'EXM-102' },
  '21MAB201T': { courseName: 'Probability and Statistics', courseType: 'THEORY', marks: 70, grade: 'B+', publishedDaysAgo: 12, examinerId: 'EXM-103' },
  '21CSE251T': { courseName: 'Artificial Intelligence', courseType: 'THEORY', marks: 55, grade: 'C', publishedDaysAgo: 1, examinerId: 'EXM-103' },
  '21CSE735P': { courseName: 'Workflow Automation Engineering', courseType: 'THEORY', marks: 58, grade: 'B', publishedDaysAgo: 2, examinerId: 'EXM-105' },
};


const state = {
  resultUpdates: [],       // what "Publish result" wrote
  paymentLinks: new Map(), // payment link id -> Razorpay-shaped payment link
  refunds: [],
  outbox: [],              // payment-link emails "sent" by Razorpay
  letters: new Map(),      // file name -> { html, requestId, createdAt }
  log: [],                 // recent API calls, shown on the dashboard
  resultsDown: false,      // dashboard switch: results system rejects every update   -> RESULT_UPDATE_FAILED
  paymentsDown: false,     // dashboard switch: payment API returns 503                 -> PAYMENT_FAILED
  lettersDown: false,      // dashboard switch: document store returns 503              -> worker retries, then incident
};

function logCall(method, path, status, note = '') {
  state.log.unshift({ at: new Date().toLocaleTimeString(), method, path, status, note });
  state.log.length = Math.min(state.log.length, 40);
  console.log(`${new Date().toLocaleTimeString()}  ${method.padEnd(5)} ${path} -> ${status}${note ? '  ' + note : ''}`);
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

function send(res, status, body, type = 'application/json') {
  res.writeHead(status, { 'content-type': `${type}; charset=utf-8` });
  res.end(type === 'application/json' ? JSON.stringify(body, null, 2) : body);
}

async function readBody(req) {
  let data = '';
  for await (const chunk of req) data += chunk;
  if (!data) return {};
  if ((req.headers['content-type'] || '').includes('application/x-www-form-urlencoded')) {
    return Object.fromEntries(new URLSearchParams(data));
  }
  try { return JSON.parse(data); } catch { return {}; }
}

// Razorpay returns errors in this shape, so the connector sees the same thing it would see from Razorpay.
function razorpayError(res, status, description) {
  send(res, status, { error: { code: 'BAD_REQUEST_ERROR', description, source: 'business', step: 'payment_initiation', reason: 'input_validation_failed' } });
}

// ---------------------------------------------------------------------------
// Routes
// ---------------------------------------------------------------------------

async function handle(req, res) {
  const url = new URL(req.url, BASE);
  const path = url.pathname;
  let m;

  // ---- Results system ----------------------------------------------------------
  if ((m = path.match(/^\/results\/([^/]+)$/))) {
    const key = decodeURIComponent(m[1]);              // e.g. RA2411026011139-21CSC305P
    const dash = key.lastIndexOf('-');
    const registerNo = key.slice(0, dash);
    const courseCode = key.slice(dash + 1).toUpperCase();
    const c = COURSES[courseCode];

    if (req.method === 'GET') {
      if (!c || !registerNo) {
        logCall('GET', path, 404, 'no published result');
        return send(res, 404, { error: 'Result not found' });
      }
      logCall('GET', path, 200, `${c.courseName}: ${c.marks}`);
      return send(res, 200, {
        id: key, registerNo, courseCode, courseName: c.courseName, courseType: c.courseType,
        marks: c.marks, grade: c.grade, publishedOn: dateDaysAgo(c.publishedDaysAgo), examinerId: c.examinerId,
      });
    }

    if (req.method === 'PATCH') {
      const body = await readBody(req);
      if (!c) return send(res, 404, { error: 'Result not found' });
      if (state.resultsDown || c.publishFails) {
        logCall('PATCH', path, 503, 'results system unavailable');
        return send(res, 503, { error: 'Results service unavailable' });
      }
      const update = { key, ...(body.revaluation || body), updatedAt: new Date().toISOString() };
      state.resultUpdates.unshift(update);
      logCall('PATCH', path, 200, `${update.outcome} -> ${update.finalMarks} (${update.finalGrade})`);
      return send(res, 200, { id: key, updatedAt: update.updatedAt, revaluation: update });
    }
  }

  // ---- Razorpay-compatible payment API (test mode) ---------------------------------
  if (path.startsWith('/razorpay/v1/')) {
    if (!(req.headers.authorization || '').startsWith('Basic ')) {
      logCall(req.method, path, 401, 'missing API key');
      return send(res, 401, { error: { code: 'BAD_REQUEST_ERROR', description: 'The api key provided is invalid' } });
    }
    const sub = path.slice('/razorpay/v1'.length);

    if (req.method === 'POST' && sub === '/payment_links') {
      const body = await readBody(req);
      if (state.paymentsDown) {
        logCall('POST', path, 503, 'payment service down');
        return send(res, 503, { error: { code: 'SERVER_ERROR', description: 'Payment service unavailable' } });
      }
      if (!Number.isInteger(body.amount) || body.amount < 100) return razorpayError(res, 400, 'The amount must be atleast INR 1.00');
      if (body.reference_id && [...state.paymentLinks.values()].some((l) => l.reference_id === body.reference_id)) {
        logCall('POST', path, 400, 'duplicate reference_id');
        return razorpayError(res, 400, `Payment Link with reference ${body.reference_id} already exists`);
      }
      const linkId = newId('plink');
      const link = {
        id: linkId, entity: 'payment_link', amount: body.amount, amount_paid: 0, currency: body.currency || 'INR',
        accept_partial: false, description: body.description || '', reference_id: body.reference_id || '',
        customer: body.customer || {}, notify: body.notify || {}, notes: body.notes || {},
        status: 'created', payments: null, short_url: `${BASE}/pay/${linkId}`,
        created_at: nowSec(), updated_at: nowSec(), expire_by: body.expire_by || 0,
      };
      state.paymentLinks.set(linkId, link);
      if (link.notify.email && link.customer.email) {
        // Real Razorpay emails the link to the customer when notify.email is true.
        state.outbox.unshift({
          id: newId('mail'), at: new Date().toISOString(), from: 'Razorpay (test mode)', to: link.customer.email,
          subject: `Payment request: ${link.description}`,
          message: `Please pay INR ${(link.amount / 100).toFixed(2)} for "${link.description}". Pay here: ${link.short_url}  (reference ${link.reference_id})`,
        });
      }
      logCall('POST', path, 200, `${linkId} for ${link.reference_id}, INR ${link.amount / 100}`);
      return send(res, 200, link);
    }

    if (req.method === 'GET' && sub === '/payment_links') {
      const links = [...state.paymentLinks.values()].sort((a, b) => b.created_at - a.created_at);
      return send(res, 200, { payment_links: links, count: links.length });
    }

    if (req.method === 'GET' && (m = sub.match(/^\/payment_links\/([^/]+)$/))) {
      const link = state.paymentLinks.get(m[1]);
      return link ? send(res, 200, link) : razorpayError(res, 404, 'The id provided does not exist');
    }

    if (req.method === 'POST' && (m = sub.match(/^\/payments\/([^/]+)\/refund$/))) {
      const paymentId = m[1];
      const body = await readBody(req);
      const link = [...state.paymentLinks.values()].find((l) => (l.payments || []).some((p) => p.payment_id === paymentId));
      if (!link) {
        logCall('POST', path, 400, 'unknown payment');
        return razorpayError(res, 400, 'The id provided does not exist');
      }
      const refund = {
        id: newId('rfnd'), entity: 'refund', amount: body.amount || link.amount_paid, currency: 'INR', payment_id: paymentId,
        notes: body.notes || {}, status: 'processed', speed_processed: 'normal', created_at: nowSec(),
      };
      state.refunds.unshift(refund);
      logCall('POST', path, 200, `${refund.id}, INR ${refund.amount / 100}`);
      return send(res, 200, refund);
    }
    return razorpayError(res, 404, 'The requested URL was not found on the server.');
  }

  // ---- Test checkout page (the student pays here) -------------------------------------
  if ((m = path.match(/^\/pay\/([^/]+)$/))) {
    const link = state.paymentLinks.get(m[1]);
    if (!link) return send(res, 404, page('Payment link not found', '<p>This payment link does not exist. Restarting the services clears all links.</p>'), 'text/html');
    if (req.method === 'POST' && link.status === 'created') {
      const paymentId = newId('pay');
      link.status = 'paid';
      link.amount_paid = link.amount;
      link.updated_at = nowSec();
      link.payments = [{ amount: link.amount, created_at: nowSec(), method: 'upi', payment_id: paymentId, plink_id: link.id, status: 'captured', updated_at: nowSec() }];
      logCall('POST', path, 200, `${paymentId} captured for ${link.reference_id}`);
    }
    const paid = link.status === 'paid';
    return send(res, 200, page(paid ? 'Payment successful' : 'Pay revaluation fee', `
      <div class="card">
        <p class="muted">Test checkout · payment link ${esc(link.id)}</p>
        <h2>INR ${(link.amount / 100).toFixed(2)}</h2>
        <p>${esc(link.description)}</p>
        <p class="muted">Reference ${esc(link.reference_id)} · ${esc(link.customer.name || '')}</p>
        ${paid
          ? `<p class="ok">Paid · ${esc(link.payments[0].payment_id)}</p><p class="muted">The payment bridge in the Spring Boot backend tells Camunda within about 5 seconds.</p>`
          : `<form method="post"><button>Pay with UPI (test)</button></form>`}
      </div>`), 'text/html');
  }

  // ---- Document store for letters -----------------------------------------------------
  if (req.method === 'POST' && path === '/letters') {
    const body = await readBody(req);
    if (state.lettersDown) {
      logCall('POST', path, 503, 'document store down');
      return send(res, 503, { error: 'Document store unavailable' });
    }
    if (!body.fileName || !body.html) return send(res, 400, { error: 'fileName and html are required' });
    const fileName = String(body.fileName).replace(/[^A-Za-z0-9._-]/g, '_');
    state.letters.set(fileName, { html: body.html, requestId: body.requestId, createdAt: new Date().toISOString() });
    logCall('POST', path, 201, fileName);
    return send(res, 201, { fileName, url: `${BASE}/letters/${encodeURIComponent(fileName)}` });
  }
  if (req.method === 'GET' && (m = path.match(/^\/letters\/([^/]+)$/))) {
    const doc = state.letters.get(decodeURIComponent(m[1]));
    return doc ? send(res, 200, doc.html, 'text/html') : send(res, 404, page('Not found', '<p>No such letter.</p>'), 'text/html');
  }

  // ---- Dashboard ---------------------------------------------------------------------
  if (req.method === 'POST' && path === '/admin/toggle') {
    const body = await readBody(req);
    if (['resultsDown', 'paymentsDown', 'lettersDown'].includes(body.name)) state[body.name] = !state[body.name];
    logCall('POST', path, 200, `${body.name} = ${state[body.name]}`);
    res.writeHead(303, { location: '/' });
    return res.end();
  }
  if (req.method === 'GET' && path === '/api/state') {
    return send(res, 200, {
      resultsDown: state.resultsDown, paymentsDown: state.paymentsDown, lettersDown: state.lettersDown, resultUpdates: state.resultUpdates,
      refunds: state.refunds, outbox: state.outbox, letters: [...state.letters.keys()], paymentLinks: [...state.paymentLinks.values()],
    });
  }
  if (req.method === 'GET' && path === '/') return send(res, 200, dashboard(), 'text/html');
  if (req.method === 'GET' && path === '/outbox') return send(res, 200, outboxPage(), 'text/html');
  if (req.method === 'GET' && path === '/health') return send(res, 200, { status: 'UP' });

  logCall(req.method, path, 404);
  return send(res, 404, { error: 'Unknown endpoint', path });
}

// ---------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------

function page(title, body, refresh = false) {
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
${refresh ? '<meta http-equiv="refresh" content="4">' : ''}<title>${esc(title)}</title>
<style>
 body{font-family:system-ui,Segoe UI,Arial,sans-serif;background:#F6F4EF;color:#13233F;margin:0;padding:24px}
 h1{font-size:22px;margin:0 0 4px} h2{font-size:17px;margin:24px 0 8px} .muted{color:#5A6270;font-size:13px}
 table{border-collapse:collapse;width:100%;background:#fff;font-size:13px} th,td{border:1px solid #DDD8CC;padding:6px 8px;text-align:left;vertical-align:top}
 th{background:#13233F;color:#F3EFE6} .card{background:#fff;border:1px solid #DDD8CC;border-radius:10px;padding:20px;max-width:440px}
 button{background:#24589C;color:#fff;border:0;border-radius:6px;padding:8px 14px;font-size:14px;cursor:pointer}
 button.warn{background:#B4501E} .ok{color:#1F7A3D;font-weight:600} .bad{color:#B4501E;font-weight:600}
 .grid{display:grid;grid-template-columns:1fr 1fr;gap:24px} @media(max-width:900px){.grid{grid-template-columns:1fr}}
 a{color:#24589C}
</style></head><body>${body}</body></html>`;
}

function dashboard() {
  const links = [...state.paymentLinks.values()].sort((a, b) => b.created_at - a.created_at);
  const toggle = (name, down, label) => `<form method="post" action="/admin/toggle" style="display:inline">
    <input type="hidden" name="name" value="${name}"><button class="${down ? 'warn' : ''}">${label}: ${down ? 'DOWN (click to restore)' : 'up (click to break)'}</button></form>`;
  const empty = (cols) => `<tr><td colspan="${cols}" class="muted">None yet</td></tr>`;
  return page('Revaluation demo services', `
  <h1>External systems for the revaluation workflow</h1>
  <p class="muted">Refreshes every 4 seconds · <a href="/outbox">Payment-link emails</a> · <a href="http://localhost:8025" target="_blank">Mailpit (process emails)</a> · <a href="http://localhost:8080/operate" target="_blank">Operate</a></p>
  <p>${toggle('resultsDown', state.resultsDown, 'Results system')} &nbsp; ${toggle('paymentsDown', state.paymentsDown, 'Payment API')} &nbsp; ${toggle('lettersDown', state.lettersDown, 'Document store')}</p>
  <div class="grid"><div>
  <h2>Payment links (Razorpay test API)</h2>
  <table><tr><th>Reference</th><th>Amount</th><th>Status</th><th></th></tr>
  ${links.map((l) => `<tr><td>${esc(l.reference_id)}<br><span class="muted">${esc(l.id)}</span></td><td>INR ${(l.amount / 100).toFixed(0)}</td>
   <td class="${l.status === 'paid' ? 'ok' : ''}">${esc(l.status)}</td><td><a href="/pay/${esc(l.id)}" target="_blank">${l.status === 'paid' ? 'View' : 'Pay'}</a></td></tr>`).join('') || empty(4)}
  </table>
  <h2>Results updated by the process</h2>
  <table><tr><th>Request</th><th>Outcome</th><th>Final</th></tr>
  ${state.resultUpdates.map((r) => `<tr><td>${esc(r.requestId)}</td><td>${esc(r.outcome)}</td><td>${esc(r.finalMarks)} (${esc(r.finalGrade)})</td></tr>`).join('') || empty(3)}
  </table>
  <h2>Refunds</h2>
  <table><tr><th>Refund</th><th>Payment</th><th>Amount</th></tr>
  ${state.refunds.map((r) => `<tr><td>${esc(r.id)}</td><td>${esc(r.payment_id)}</td><td class="ok">INR ${(r.amount / 100).toFixed(0)} ${esc(r.status)}</td></tr>`).join('') || empty(3)}
  </table>
  <h2>Letters issued</h2>
  <table>${[...state.letters.keys()].map((f) => `<tr><td><a href="/letters/${encodeURIComponent(f)}" target="_blank">${esc(f)}</a></td></tr>`).join('') || empty(1)}</table>
  </div><div>
  <h2>Payment-link emails (sent by Razorpay)</h2>
  <table><tr><th>To</th><th>Subject</th></tr>
  ${state.outbox.slice(0, 6).map((e) => `<tr><td>${esc(e.to)}<br><span class="muted">${new Date(e.at).toLocaleTimeString()} · ${esc(e.from)}</span></td><td>${esc(e.subject)}</td></tr>`).join('') || empty(2)}
  </table>
  <h2>API calls</h2>
  <table><tr><th>Time</th><th>Call</th><th>Status</th></tr>
  ${state.log.slice(0, 12).map((l) => `<tr><td>${esc(l.at)}</td><td>${esc(l.method)} ${esc(l.path)}<br><span class="muted">${esc(l.note)}</span></td><td class="${l.status >= 400 ? 'bad' : ''}">${l.status}</td></tr>`).join('') || empty(3)}
  </table>
  </div></div>`, true);
}

function outboxPage() {
  return page('Payment-link emails', `<h1>Payment-link emails</h1><p class="muted"><a href="/">Back to dashboard</a> · refreshes every 4 seconds</p>
  ${state.outbox.map((e) => `<div class="card" style="max-width:none;margin-bottom:12px">
   <p class="muted">${new Date(e.at).toLocaleString()} · from ${esc(e.from)} · to <b>${esc(e.to)}</b></p>
   <p><b>${esc(e.subject)}</b></p><p>${esc(e.message)}</p></div>`).join('') || '<p class="muted">No emails yet.</p>'}`, true);
}

// ---------------------------------------------------------------------------

http.createServer((req, res) => {
  handle(req, res).catch((err) => {
    console.error(err);
    send(res, 500, { error: 'Internal error', message: err.message });
  });
}).listen(PORT, () => {
  console.log(`Mock external systems on ${BASE}   (dashboard ${BASE}/   outbox ${BASE}/outbox)`);
  console.log(`Test courses: ${Object.keys(COURSES).join(', ')}   (any other code -> 404)`);
});
