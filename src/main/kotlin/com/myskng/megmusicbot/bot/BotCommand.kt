package com.myskng.megmusicbot.bot

import com.myskng.megmusicbot.database.SearchQuery
import com.myskng.megmusicbot.database.SongSearchType
import com.myskng.megmusicbot.exception.CommandSyntaxException
import discord4j.core.event.domain.message.MessageCreateEvent
import org.koin.core.KoinComponent
import org.koin.core.inject
import picocli.CommandLine

class BotCommand : KoinComponent {
    val processor by inject<BotCommandProcessor>()

    class DiscordCommandLine {
        @CommandLine.Option(names = ["title"])
        var title: String? = null

        @CommandLine.Option(names = ["artist"])
        var artist: String? = null

        @CommandLine.Option(names = ["album"])
        var album: String? = null

        @CommandLine.Option(names = ["/help"])
        var isHelp = false

        @CommandLine.Option(names = ["/join"])
        var isJoin = false

        @CommandLine.Option(names = ["/leave"])
        var isLeave = false

        @CommandLine.Option(names = ["/search"])
        var isSearch = false

        @CommandLine.Option(names = ["/play"])
        var play: Int = -1

        @CommandLine.Option(names = ["/queue"])
        var isQueue = false

        @CommandLine.Option(names = ["/skip"])
        var isSkip = false
    }

    fun splitCommandToArray(command: String): Array<String> {
        val regex = Regex("\"(\"|(?!\").)+\"|[^ ]+")
        val resultList = mutableListOf<String>()
        regex.findAll(command).forEach {
            var match = it.value
            match = match.removePrefix("\"")
            match = match.removeSuffix("\"")
            resultList.add(match)
        }
        return resultList.toTypedArray()
    }

    fun isBotCommand(command: String): Boolean {
        val commandArray = splitCommandToArray(command)
        return CommandLine(DiscordCommandLine()).commandSpec.optionsMap()
            .any { it.key == commandArray.firstOrNull() }
    }

    suspend fun onCommandRecive(command: String, event: MessageCreateEvent) {
        val discordCommandLine = DiscordCommandLine()
        try {
            CommandLine(discordCommandLine).parseArgs(*splitCommandToArray(command))
        } catch (ex: CommandLine.PicocliException) {
            throw CommandSyntaxException("コマンド解析中にエラーが発生しました: ${ex.message}")
        }
        when {
            discordCommandLine.isHelp -> {
                processor.outputHelpText(event)
            }
            discordCommandLine.isJoin -> {
                processor.joinVoiceChannel(event)
            }
            discordCommandLine.isLeave -> {
                processor.leaveVoiceChannel(event)
            }
            discordCommandLine.isSearch -> {
                val queryList = mutableListOf<SearchQuery>()
                if (discordCommandLine.album != null) {
                    queryList.add(SearchQuery(SongSearchType.Album, discordCommandLine.album!!))
                }
                if (discordCommandLine.artist != null) {
                    queryList.add(SearchQuery(SongSearchType.Artist, discordCommandLine.artist!!))
                }
                if (discordCommandLine.title != null) {
                    queryList.add(SearchQuery(SongSearchType.Title, discordCommandLine.title!!))
                }
                if (queryList.isEmpty()) {
                    throw CommandSyntaxException("検索条件が空です。")
                }
                processor.searchSong(queryList.toTypedArray(), event)
            }
            discordCommandLine.play != -1 -> {
                processor.playSong(discordCommandLine.play - 1, event)
            }
            discordCommandLine.isQueue -> {
                processor.printQueue(event)
            }
            discordCommandLine.isSkip -> {
                processor.skipSong()
            }
            else -> {
                throw CommandSyntaxException("コマンドの記法が間違っています。")
            }
        }
    }
}
