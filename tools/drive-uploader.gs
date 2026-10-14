/**
 * 自作アプリAPK を Google Drive に保存し、同じアプリの古いAPKをゴミ箱へ移す。
 * 全アプリ共通。GASに1回だけデプロイすれば、どのリポジトリからも使える。
 * 呼び出し: POST {URL}?key=..&app=アプリ名&ver=版  本文=APKのbase64
 */
const SECRET_KEY = 'ここに合言葉を入れる';
const ROOT_FOLDER = '自作アプリAPK';

function doPost(e) {
  const p = e.parameter;
  if (p.key !== SECRET_KEY) return out({ ok: false, error: 'bad key' });
  const app = String(p.app || '').replace(/[^\w.-]/g, '');
  if (!app) return out({ ok: false, error: 'no app' });
  const ver = String(p.ver || 'dev').replace(/[^\w.-]/g, '');

  const root = getOrCreate(DriveApp.getRootFolder(), ROOT_FOLDER);
  const folder = getOrCreate(root, app);
  const name = app + '_' + ver + '.apk';
  const blob = Utilities.newBlob(
    Utilities.base64Decode(e.postData.contents),
    'application/vnd.android.package-archive', name);
  const file = folder.createFile(blob);

  // 古いAPKをゴミ箱へ（30日間は復元可）
  let trashed = 0;
  const it = folder.getFiles();
  while (it.hasNext()) {
    const f = it.next();
    if (f.getId() !== file.getId()) { f.setTrashed(true); trashed++; }
  }
  return out({ ok: true, file: name, url: file.getUrl(), folder: folder.getUrl(), trashed: trashed });
}

function getOrCreate(parent, name) {
  const it = parent.getFoldersByName(name);
  return it.hasNext() ? it.next() : parent.createFolder(name);
}
function out(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
/** デプロイ前に1回実行して、Driveの権限を許可するための関数 */
function authorize() { getOrCreate(DriveApp.getRootFolder(), ROOT_FOLDER); }
