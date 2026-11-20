# Jihou 0.0.0-beta

曜日と実時刻を使い、オンラインプレイヤー全員へ通知するDedicated Server専用Fabric Modです。
Minecraft 26.3 / Fabric Loader 0.20.1以上 / Fabric API 0.165.0+26.3 / Java 25。
packageは `com.jihoumod`、mod IDと既存Gradleプロジェクト名は `jihou` です。
クライアントへのMod導入は不要です。ライセンスはMPL-2.0です。

## 導入・設定

1. `gradlew.bat build` で作成する `build/libs/jihou-0.0.0-beta.jar` とFabric APIをサーバーの `mods/` に入れます。
2. 起動するとサーバーディレクトリの `config/jihou.conf` にUTF-8のサンプルを生成します。既存ファイルは上書きしません。
3. 設定を編集し、OPから `/jihou reload` を実行します。コンソールでは `jihou reload` です。OPレベル1以上が実行可能です。

```conf
timezone: "Asia/Tokyo"

everyday {
    notification {
        time: "23:55"
        type: title
        message: "<gold><bold>就寝時間まで残り5分です<reset>"
        subtitle: "そろそろ準備してください"
    }
}
monday {
    notification {
        time: "24:00"
        type: chat
        message: "月曜日が終了しました"
    }
}
tuesday {
    notification {
        time: "00:00"
        type: subtitle
        message: "火曜日です"
    }
}
```

`everyday` と `monday`～`sunday` が使えます。同じ曜日ブロックを複数書くと両方有効になります。
`timezone` は省略時 `Asia/Tokyo`、指定時はJavaの `ZoneId` として検証します。
`time`、`type`、`message` は必須、`subtitle` は任意です。
`#` は文字列の外で行末までのコメントになります。カンマは使いません。
文字列内では `\"`、`\\`、`\n`、`\r`、`\t` が使えます。
時刻は2桁の `00:00`～`23:59` と `24:00` に対応します。

`24:00` は前日のスケジュールとして翌日0時に実行します。
上の例では月曜日 `24:00` と火曜日 `00:00` の両方が火曜日0時に実行されます。
通知IDは全ブロックを通じてファイルの記述順に1から付与します。

## 通知・装飾

| type | 表示 |
| --- | --- |
| `chat` | 接頭辞なしのシステムチャット |
| `actionbar` | Action Bar |
| `title` | messageをTitle、任意のsubtitleをSubtitleに表示 |
| `subtitle` | 空のTitleとmessageをSubtitleに表示 |

任意キー `subtitle` は `type: title` の表示に使います。他のtypeでも指定内容の装飾は検証しますが、表示はしません。
Title系は設定記述順のFIFOキューで、標準の表示時間（10 + 70 + 20 tick、通常5秒）ずつ表示します。
chat/actionbarはキューを待たず実行します。送信時点のオンラインプレイヤーを対象とし、全員オフラインのときはキューを破棄します。

`message` と `subtitle` で以下の装飾が使えます。

- 色: Minecraftの16色名（例: `<red>`、`<gold>`、`<dark_blue>`）と `<#FF5500>` の6桁HEX
- 装飾: `<bold>`、`<italic>`、`<underlined>`、`<strikethrough>`、`<obfuscated>`
- リセット: `<reset>`

開始タグは次の指定または文末まで有効で、閉じタグの省略を許可します。
閉じタグを書く場合は `<red><bold>文字</bold></red>` のように逆順で閉じます。
resetは全装飾とタグのスタックをクリアします。不明タグ、不正HEX、不一致の閉じタグは設定エラーです。
リテラルの `<` / `>` はconfの文字列内で `\\<` / `\\>` と書けます。

## エラー・時刻判定

構文、必須キー、時刻、type、タイムゾーン、装飾をすべて検証してから設定を切り替えます。
エラーには行番号と原因を表示します。起動時に不正なら通知のみ無効になり、サーバーは起動を続けます。
reload失敗時は直前の正常な設定とTitleキューを維持します。
reload成功時は新設定に切り替えて待機中の旧Titleキューを破棄します。

判定とプレイヤーへの送信はサーバースレッドのtickイベント内で実行します。
同じ分は一度だけ確認し、日付と内部IDでも重複を防止します。夏時間で同じ時刻が2回来ても1回だけ実行します。
起動・reloadが成功した分は実行せず、次の分から判定します。停止中・tick停止中に通過した分へのcatch-upはしません。
時計が巻き戻った場合は、最後に確認した実時刻の分を超えるまで新しい通知を実行しません。
Dedicated Serverの `pause-when-empty-seconds` による停止中はtickイベントが発生しません。

通知音、GUI、履歴、手動ID、表示時間の設定は初期版に含みません。
他Modや `/title` コマンドによるTitleの上書きまでは制御しません。

## ビルド・テスト

Java 25を `JAVA_HOME` に設定して実行してください。Minecraft/Loader/Fabric API/Java/Gradleの既存バージョン設定は維持しています。

```powershell
.\gradlew.bat build
.\gradlew.bat unitTest
```

追加ライブラリを必要としない `src/unitTest/java` のテストランナーをGradleの `check` / `build` に接続しています。
設定生成と非上書き、曜日、全type、装飾、構文・行番号、24:00、同一分重複、時計巻き戻り、
夏時間、catch-up抑止、2件/4件Titleキュー、設定A→破損B→失敗後もA維持→正常Bへ切替を検証します。
Minecraftクライアントの実画面表示は別途、実サーバーへ接続して確認してください。

主な構成は `config/`（検証済みモデルと読込）、`text/`（装飾解析とComponent変換）、
`scheduler/`（時刻判定）、`notification/`（送信とFIFO）、`command/`（reload）、`JihouRuntime`（実行制御）です。
