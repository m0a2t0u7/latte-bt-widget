# AI向け引き継ぎガイド（Claude / ChatGPT 共通）

## このリポジトリ
- Androidウィジェットアプリ「Bluetoothウィジェット｜ラテ」（旧名ラテBT）（ウィジェット1×1／2×1／4×1）
- Bluetoothの接続状態と接続中の機器名を表示し、切断・再接続・他機器への切替（失敗時は設定画面を開く）
- ウィジェット（1×1/2×1/4×1）と常時通知の両方から同じ操作ができる
- BT本体のオン/オフはAndroid 13以降アプリから不可のため設定画面を開く
- Kotlin、ライブラリ依存なし、minSdk 26 / targetSdk 34

## 修正の流れ
1. `app/src/main/` 以下を修正して `main` にpushするだけ
2. GitHub Actions（`.github/workflows/build.yml`）が自動で
   - APKをビルド
   - 版・コミット別Releaseを追加して旧版を保持
   - 旧版を保持できるGASへ再デプロイ済みのときのみDrive自動保存（`GAS_PRESERVES_OLD_APKS=true`）。それまでは接続済みDriveツールでAPK実体を保存し、サイズ・親・署名・台帳を確認する
3. ローカルビルド環境は不要。手元でビルドしようとしないこと

## 完了報告に必ず入れるもの
- 最新版ダウンロード: https://github.com/m0a2t0u7/latte-bt-widget/releases/latest/download/latte-bt-widget.apk
- ビルド状況: https://github.com/m0a2t0u7/latte-bt-widget/actions

## 主要ファイル
| ファイル | 役割 |
|---|---|
| `BtWidgetProvider.kt` | 全サイズのウィジェット表示と自動更新（Small=1×1, Medium=2×1, 本体=4×1） |
| `ActionActivity.kt` | 画面なしで 切断／再接続／切替(connect)／トグル を実行 |
| `PickerActivity.kt` | ペアリング済みオーディオ機器の一覧、タップで切替・切断 |
| `Notif.kt` / `BtStatusService.kt` | 常時通知（前面サービス）と再起動時の復帰 |
| `Ui.kt` | 状態取得とウィジェット・通知の一括更新 |
| `Bt.kt` | Bluetooth共通処理（非公開API disconnect をリフレクションで呼ぶ） |
| `MainActivity.kt` | 権限許可と使い方表示 |
| `tools/drive-uploader.gs` | Drive保存用GAS（全アプリ共通、デプロイ済みのものを使う） |

## 注意
- `build.yml` の Release／Drive の手順は消さない
- `app/debug.keystore` と `build.gradle.kts` の signingConfigs は消さない（消すと上書きインストールできなくなる）
- Secrets `GAS_URL` `GAS_KEY` はリポジトリ設定にある。コードに直書きしない

## 1.1 更新
- 名称を機能がわかる「Bluetoothウィジェット｜ラテ」に変更。スマホ画面＋ウィジェットの採用②アイコン。
- 外観はライト／ダーク／端末設定。通知自体はAndroidが配色を決める。
- 外観設定は独立したappearance設定に保存。既存のBluetooth機器／通知設定は維持。
- 端末テーマ連動はAPI31以降、RemoteViewsの色リソースとday/night drawableでホスト再描画時にも反映する。
- ビルド、lint、旧APK署名比較、エミュレータ表示の証拠をActions artifactへ保存。実際のBluetooth機器接続は実機未確認。
- 旧GASエンドポイントは古いAPKを削除するため、そのまま再有効化しない。tools/drive-uploader.gsの保持版をデプロイ後だけGAS_PRESERVES_OLD_APKSをtrueにする。
