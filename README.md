# iugytf

![screenshot](screenshot.png)

Telegram 颜文字 Bot <http://telegram.me/ywzbot>

在输入框中输入 `@ywzbot` 使用

私聊可使用的命令:

<table>
  <tr>
    <td>/list</td>
    <td>显示颜文字列表加 ID</td>
  </tr>
  <tr>
    <td>/list_raw</td>
    <td>只显示颜文字列表</td>
  </tr>
  <tr>
    <td>/del</td>
    <td>命令后加 ID 或者颜文字进行删除</td>
  </tr>
  <tr>
    <td>/add</td>
    <td>命令后加颜文字进行添加</td>
  </tr>
  <tr>
    <td>/sort</td>
    <td>重新分配 ID</td>
  </tr>
</table>

## Run

    $ java -jar iugytf-0.1.0-standalone.jar [config-file]

需先在 [@BotFather](https://telegram.me/BotFather) 里 `/setinline` `/setinlinefeedback`

## License

The MIT License (MIT)

Copyright © 2016 iovxw
