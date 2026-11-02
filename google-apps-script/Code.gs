/**
 * Namakkal Market Android feed.
 * Deploy as a Web app: Execute as Me; access Anyone.
 * Reads the current month tab and returns dated low/high values.
 */
function doGet(e) {
  const ss = SpreadsheetApp.openById('16-u9MpIaCPsT7wfby8CoM-80zThyOUXvNvOxV4WGyNk');
  const now = new Date();
  const tz = ss.getSpreadsheetTimeZone() || 'Asia/Kolkata';
  const monthName = Utilities.formatDate(now, tz, 'MMMM yyyy');
  const sheet = ss.getSheetByName(monthName);
  if (!sheet) {
    return json({ok:false, error:'Missing month tab: ' + monthName, month:monthName});
  }

  const values = sheet.getDataRange().getDisplayValues();
  if (values.length < 2) return json({ok:true, month:monthName, rows:[]});

  const headers = values[0];
  const dateCols = [];
  for (let c = 3; c < headers.length; c += 2) {
    const m = String(headers[c] || '').match(/(\d{4}-\d{2}-\d{2})\s*\(Low\)/i);
    if (!m) continue;
    dateCols.push({date:m[1], low:c, high:c+1});
  }

  const rows = [];
  for (let r = 1; r < values.length; r++) {
    const row = values[r];
    const tamil = String(row[0] || '').trim();
    const english = String(row[1] || '').trim();
    if (!tamil && !english) continue;
    const unit = String(row[2] || 'Per Kg').trim();
    const history = [];
    dateCols.forEach(d => {
      const low = numeric(row[d.low]);
      const high = numeric(row[d.high]);
      if (low !== null && high !== null && high >= low) {
        history.push({date:d.date, low:low, high:high});
      }
    });
    if (history.length) rows.push({tamil:tamil, english:english, unit:unit, history:history});
  }

  return json({
    ok:true,
    spreadsheet:'blueticks price',
    month:monthName,
    updatedAt:new Date().toISOString(),
    rows:rows
  });
}

function numeric(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null;
  const n = Number(String(value).replace(/[^0-9.\-]/g, ''));
  return isFinite(n) && n > 0 ? n : null;
}

function json(value) {
  return ContentService.createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}
