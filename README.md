
![](http://i.imgur.com/FtFOvKJ.png)
mercury
========

[![Join the chat at https://gitter.im/mappum/mercury](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/mappum/mercury?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A multi-coin wallet that supports trustless cross-chain trading.

## Status

Mercury is being actively developed, but I am moving from the current Java desktop application to a decentralized web app, built on [Webcoin](https://github.com/mappum/webcoin) (SPV client that uses WebRTC). The existing Java releases are available for testing, but will not be developed further.

For further updates, please sign up for the mailing list here: http://mercuryex.com#emailupdates

## Download

We recommend having [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html) installed.

* [Windows (.exe)](https://github.com/mappum/mercury/releases/download/0.0.2-alpha/MercuryWallet-0.0.2.exe)
* [OSX (.dmg)](https://github.com/mappum/mercury/releases/download/0.0.2-alpha/MercuryWallet-0.0.2.dmg)
* [Linux (.jar)](https://github.com/mappum/mercury/releases/download/0.0.2-alpha/MercuryWallet-0.0.2.jar)

## Build

You might want to build Mercury if you don't want to trust a pre-built binary, or you are making changes to the code.

Required:

* [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [Maven 3](http://maven.apache.org/download.cgi)

Clone the project and build it:
```
$ git clone https://github.com/mappum/mercury
$ cd mercury
$ mvn clean install
```
This will create the files you want to run in a directory called `target`. There will be a `.jar`, `.exe`, and on OSX a `.app` and `.dmg`.

## Contributing

Mercury is is need of testing and code contributions. Please feel free to file issues if you find any bugs.

If any critical exploits are found in the cross-chain atomic swap implementation, we will gladly pay bounties if they are disclosed responsibly (there is bound to be some low-hanging fruit to find): https://www.crowdcurity.com/mercury

Join the Mercury development community in the IRC channel, #hg on Freenode.

## License (MIT)

Copyright 2015 Mercury

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
