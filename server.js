const express = require('express');
const Database = require('better-sqlite3');
const nodemailer = require('nodemailer');
const path = require('path');
require('dotenv').config();

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));
const db = new Database(process.env.DB_PATH || path.join(__dirname, 'vastav.sqlite'));
db.pragma('journal_mode = WAL');
db.exec(`
CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY, rid TEXT UNIQUE NOT NULL, at INTEGER NOT NULL,
  name TEXT, phone TEXT, email TEXT, description TEXT, location TEXT, landmark TEXT,
  category TEXT, department TEXT, priority INTEGER, explanation TEXT, status TEXT,
  note TEXT, severity INTEGER, people_affected INTEGER, days INTEGER, recurrence INTEGER,
  safety INTEGER, cost REAL, map_url TEXT, photo TEXT, lat REAL, lng REAL,
  created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS reports_location ON reports(location);
CREATE INDEX IF NOT EXISTS reports_status ON reports(status);
`);
const insert = db.prepare(`INSERT INTO reports
(id,rid,at,name,phone,email,description,location,landmark,category,department,priority,explanation,status,note,severity,people_affected,days,recurrence,safety,cost,map_url,photo,lat,lng)
VALUES (@id,@rid,@at,@name,@phone,@email,@description,@location,@landmark,@category,@department,@priority,@explanation,@status,@note,@severity,@people_affected,@days,@recurrence,@safety,@cost,@map_url,@photo,@lat,@lng)`);
function fromClient(r) {
  return {
    id: String(r.id || r.clientId || r.rid || ''), rid: String(r.rid || ''),
    at: Number(r.at || Date.now()), name: String(r.name || '').slice(0,150),
    phone: String(r.phone || '').slice(0,40), email: String(r.email || '').slice(0,254),
    description: String(r.desc || r.description || '').slice(0,10000),
    location: String(r.loc || r.location || '').slice(0,500),
    landmark: String(r.lmk || r.landmark || '').slice(0,500),
    category: String(r.cat || r.category || 'General Maintenance').slice(0,150),
    department: String(r.dep || r.department || '').slice(0,150),
    priority: Number(r.p || r.priority || 0), explanation: String(r.ex || r.explanation || '').slice(0,2000),
    status: String(r.st || r.status || 'SUBMITTED').slice(0,40), note: String(r.note || '').slice(0,3000),
    severity: Number(r.sev || r.severity || 0), people_affected: Number(r.pop || r.people_affected || 0),
    days: Number(r.days || 0), recurrence: Number(r.rec || r.recurrence || 0),
    safety: Number(r.saf || r.safety || 0), cost: Number(r.cost || 0),
    map_url: String(r.map || r.map_url || '').slice(0,2000), photo: String(r.photo || '').slice(0,2000),
    lat: r.lat == null ? null : Number(r.lat), lng: r.lng == null ? null : Number(r.lng)
  };
}
function toClient(r) {
  return {id:r.id,rid:r.rid,at:r.at,name:r.name,phone:r.phone,email:r.email,desc:r.description,loc:r.location,lmk:r.landmark,cat:r.category,dep:r.department,p:r.priority,ex:r.explanation,st:r.status,note:r.note,sev:r.severity,pop:r.people_affected,days:r.days,rec:r.recurrence,saf:r.safety,cost:r.cost,map:r.map_url,photo:r.photo,lat:r.lat,lng:r.lng};
}
let transporter = null;
if (process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS) {
  transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST, port: Number(process.env.SMTP_PORT || 587),
    secure: String(process.env.SMTP_SECURE || 'false') === 'true',
    auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS }
  });
}
async function notify(r) {
  if (!transporter || !process.env.NOTIFY_EMAIL) return;
  const subject = `VASTAV report ${r.rid}: ${r.category}`;
  const text = `A new VASTAV report was submitted.\n\nReceipt: ${r.rid}\nName: ${r.name}\nPhone: ${r.phone}\nEmail: ${r.email}\nCategory: ${r.category}\nDescription: ${r.description}\nLocation: ${r.location}\nLandmark: ${r.landmark}\nPriority: ${r.priority}\nStatus: ${r.status}\n\nOpen the VASTAV dashboard to review.`;
  try { await transporter.sendMail({from:process.env.MAIL_FROM || process.env.SMTP_USER,to:process.env.NOTIFY_EMAIL,subject,text}); }
  catch (e) { console.error('Email notification failed:', e.message); }
}
app.get('/api/health', (_req,res)=>res.json({ok:true}));
app.get('/api/public/reports', (_req,res)=>{
  const reports=db.prepare('SELECT * FROM reports ORDER BY at DESC').all();
  res.json({reports:reports.map(toClient)});
});
app.post('/api/reports', async (req,res)=>{
  const r=fromClient(req.body || {});
  if (!r.id || !r.rid || !r.name || !r.phone || !r.email || !r.description || !r.location) {
    return res.status(400).json({error:'Required report details are missing.'});
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(r.email)) return res.status(400).json({error:'Please provide a valid email address.'});
  const existing=db.prepare('SELECT * FROM reports WHERE rid=? OR id=?').get(r.rid,r.id);
  if (existing) return res.json({report:toClient(existing),alreadySaved:true});
  try {
    insert.run(r);
    const saved=db.prepare('SELECT * FROM reports WHERE id=?').get(r.id);
    // Email failure should not lose the saved report.
    notify(saved);
    res.status(201).json({report:toClient(saved)});
  } catch (e) {
    console.error('Save failed:', e.message);
    res.status(500).json({error:'Could not save this report. Please try again.'});
  }
});
app.patch('/api/reports/:rid/status', (req,res)=>{
  const token=process.env.OFFICIAL_TOKEN;
  if (!token || req.get('x-official-token') !== token) return res.status(401).json({error:'Unauthorized'});
  const allowed=['SUBMITTED','UNDER_REVIEW','APPROVED','IN_PROGRESS','RESOLVED','REJECTED'];
  const status=String(req.body.status || '');
  if (!allowed.includes(status)) return res.status(400).json({error:'Invalid status.'});
  const note=String(req.body.note || '').slice(0,3000);
  const result=db.prepare('UPDATE reports SET status=?,note=? WHERE rid=?').run(status,note,req.params.rid);
  if (!result.changes) return res.status(404).json({error:'Report not found.'});
  res.json({report:toClient(db.prepare('SELECT * FROM reports WHERE rid=?').get(req.params.rid))});
});
const port=Number(process.env.PORT || 3000);
app.listen(port, ()=>console.log(`VASTAV running at http://localhost:${port}`));
