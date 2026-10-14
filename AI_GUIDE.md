# AI向け引き継ぎガイド（Claude / ChatGPT 共通）

## このリポジトリ
- Androidウィジェットアプリ「ラテBT」（ウィジェット1×1／2×1／4×1）
- Bluetoothの接続状態と接続中の機器名を表示し、ボタンで切断（失敗時は設定画面を開く）
- Kotlin、ライブラリ依存なし、minSdk 26 / targetSdk 34

## 修正の流れ
1. `app/src/main/` 以下を修正して `main` にpushするだけ
2. GitHub Actions（`.github/workflows/build.yml`）が自動で
   - APKをビルド
   - Release `latest` を作り直して最新版を配布
   - Google Drive「自作アプリAPK/latte-bt-widget」へ保存し、古いAPKをゴミ箱へ
3. ローカルビルド環境は不要。手元でビルドしようとしないこと

## 完了報告に必ず入れるもの
- 最新版ダウンロード: https://github.com/m0a2t0u7/latte-bt-widget/releases/latest/download/latte-bt-widget.apk
- ビルド状況: https://github.com/m0a2t0u7/latte-bt-widget/actions

## 主要ファイル
| ファイル | 役割 |
|---|---|
| `BtWidgetProvider.kt` | 全サイズのウィジェット表示と自動更新（Small=1×1, Medium=2×1, 本体=4×1） |
| `DisconnectActivity.kt` | 切断ボタン処理 |
| `Bt.kt` | Bluetooth共通処理（非公開API disconnect をリフレクションで呼ぶ） |
| `MainActivity.kt` | 権限許可と使い方表示 |
| `tools/drive-uploader.gs` | Drive保存用GAS（全アプリ共通、デプロイ済みのものを使う） |

## 注意
- `build.yml` の Release／Drive の手順は消さない
- Secrets `GAS_URL` `GAS_KEY` はリポジトリ設定にある。コードに直書きしない
