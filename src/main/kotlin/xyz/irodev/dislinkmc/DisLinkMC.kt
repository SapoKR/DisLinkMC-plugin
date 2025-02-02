package xyz.irodev.dislinkmc

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.annotation.DataDirectory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.kyori.adventure.text.Component
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import java.nio.file.Path
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.TimeUnit


@Suppress("unused")
class DisLinkMC @Inject constructor(private val logger: Logger, @DataDirectory private val dataDirectory: Path) {

    private val config = Config.loadConfig(dataDirectory, logger)

    private val onSuccess: MessageFormat = MessageFormat(config.message.onSuccess)

    private val onFail: MessageFormat = MessageFormat(config.message.onFail)

    private val codeStore: Cache<String, VerifyCodeSet> = Caffeine.newBuilder()
        .expireAfterWrite(config.otp.time, TimeUnit.SECONDS)
        .build()

    private val database: Database = Database.connect(
        config.mariadb.url,
        "org.mariadb.jdbc.Driver",
        config.mariadb.user,
        config.mariadb.password
    )

    private val discord: JDA? = config.discord.token.let { token ->
        try {
            JDABuilder.createDefault(token).enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL).build().apply {
                    awaitReady()
                    getGuildById(config.discord.guildID)?.let { guild ->
                        logger.info(guild.toString())
                        guild.getRoleById(config.discord.newbieRoleID)?.let { newbieRole ->
                            logger.info(newbieRole.toString())
                            addEventListener(
                                VerifyBot(guild, newbieRole, logger, codeStore, database)
                            )
                        } ?: logger.error("Invalid Newbie Role ID. Please check config.toml")
                    } ?: logger.error("Invalid Discord Guild ID. Please check config.toml")
                }
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid Discord Bot Token. Please check config.toml")
            null
        }
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(VerifyBot.LinkedAccounts)
        }
    }

    @Subscribe
    private fun onLogin(event: LoginEvent) {
        val player = event.player
        val name = player.username
        val uuid = player.uniqueId
        try {
            var codeset: VerifyCodeSet? = codeStore.getIfPresent(name.lowercase())
            if (codeset == null) {
                codeset = VerifyCodeSet(name, uuid, (0..999999).random())
                codeStore.put(name.lowercase(), codeset)
            }
            logger.info(codeset.toString())
            player.disconnect(
                Component.text(
                    onSuccess.format(
                        arrayOf<String>(
                            name,
                            uuid.toString(),
                            String.format("%03d %03d", codeset.code / 1000, codeset.code % 1000)
                        )
                    )
                )
            )

        } catch (e: Exception) {
            player.disconnect(Component.text(onFail.format(arrayOf<String>(name, uuid.toString()))))
            e.printStackTrace()
        }
    }

    @Subscribe
    private fun onExit(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        discord?.run {
            shutdown()
            awaitShutdown()
        }
    }


    internal data class VerifyCodeSet(
        val name: String = "",
        val uuid: UUID = UUID.randomUUID(),
        val code: Int = 0
    )

}