
![](http://i.imgur.com/FtFOvKJ.png)
mercury
========

A multi-coin wallet that supports trustless cross-chain trading.

## Download

We recommend having [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html) installed.

* [Windows (.exe)](https://github.com/mappum/mercury/releases/download/0.0.1-alpha/MercuryWallet-0.0.1-SNAPSHOT.exe)
* [OSX (.dmg)](https://github.com/mappum/mercury/releases/download/0.0.1-alpha/MercuryWallet-0.0.1-SNAPSHOT.dmg)
* [Linux (.jar)](https://github.com/mappum/mercury/releases/download/0.0.1-alpha/MercuryWallet-0.0.1-SNAPSHOT.jar)

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

If any critical exploits are found in the cross-chain atomic swap implementation, we will gladly pay bounties if they are disclosed responsibly (there is bound to be some low-hanging fruit to find). [There will be a Crowdcurity link here shortly].

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
