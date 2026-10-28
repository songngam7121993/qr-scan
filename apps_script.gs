// Dán vào Google Sheet: Tiện ích mở rộng > Apps Script
function doGet(e) {
  var p = e.parameter;
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = p.sheet ? ss.getSheetByName(p.sheet) : ss.getSheets()[0];
  if (!sh) return ContentService.createTextOutput("ERR: không tìm thấy sheet");
  var cell = sh.getRange(p.col + p.row);
  cell.setNumberFormat("@");      // giữ nguyên số 0 đầu của mã vạch
  cell.setValue(p.value);
  return ContentService.createTextOutput("OK");
}
