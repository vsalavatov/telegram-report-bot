# Telegram Report Bot

This bot accumulates statistics on members activity 
and gives a _vote power_ to those who seem to be adequate.
Admins have the power to ban any normal members.

Active members can use their vote power to report messages.
If enough members consider a message harmful then the bot will kick the author 
of that message out of the group and delete the message. 

Commands:
* `/report` - Used with reply on suspicious messages. Available to all members.
Starts a vote poll. The ban is performed if number of votes reaches chat's limit;

Admin commands:
* `/setReportVoteLimit <integer>` - Sets new vote number limit for current chat;
* `/setMinutesToGainVotePower <integer>` - Sets new time period for giving vote power. 
Member will not obtain a vote power if a time period since their first message is less than specified;
* `/setMessagesToGainVotePower <integer>` - Sets new lower bound for giving vote power.
Member will not obtain a vote power until they communicate enough in the chat.

## Usage

Clone and run
```shell script
$ gradlew build installDist
```
to build and install.
Executables are located in the `build/install/telegram-report-bot/bin` subdirectory.
Run
```shell script
$ ./telegram-report-bot --help
```
to get further information.
Supports SOCKS5 proxy.