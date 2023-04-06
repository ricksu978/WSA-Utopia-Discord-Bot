package tw.waterballsa.utopia.gaas

import dev.minn.jda.ktx.generics.getChannel
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventCreateEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import tw.waterballsa.utopia.commons.config.WsaDiscordProperties
import tw.waterballsa.utopia.commons.utils.createDirectoryIfNotExists
import tw.waterballsa.utopia.commons.utils.createFileIfNotExists
import tw.waterballsa.utopia.jda.UtopiaListener
import tw.waterballsa.utopia.jda.listener
import java.io.File
import java.lang.System.lineSeparator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.time.ZoneId.systemDefault

/*
* PersonalLeave is a feature of GaaS that allows members to request time off.
* When a GaaS event is created,
* the bot automatically sends a message to remind members that they can request personal leave through it.
* */

private const val customButtonId = "gaas-leave"
private const val customModalId = "leave-modal"
private const val DATABASE_DIRECTORY = "data/gaas/leave"
private const val DATABASE_FILENAME_TEMPLATE = "/GaaS-leave-\$date.db"
private lateinit var eventTime: LocalDateTime
fun createLeaveMenuWhenGaaSEventCreated(wsaDiscordProperties: WsaDiscordProperties): UtopiaListener {
    return listener {
        on<ScheduledEventCreateEvent> {
            val partyChannelId = wsaDiscordProperties.wsaPartyChannelId
            val guildId = wsaDiscordProperties.guildId
            val gaaSConversationChannelId = wsaDiscordProperties.wsaGaaSConversationChannelId
            val wsaGaaSMemberRoleId = wsaDiscordProperties.wsaGaaSMemberRoleId
            eventTime = scheduledEvent.startTime.atZoneSameInstant(systemDefault()).toLocalDateTime()
            val conversationChannel = jda.getGuildById(guildId)!!.getChannel<TextChannel>(gaaSConversationChannelId)!!

            scheduledEvent
                .takeIf { it.name.contains("遊戲微服務") && it.channel?.id == partyChannelId }
                ?.run {
                    conversationChannel
                        .sendMessage("<@&$wsaGaaSMemberRoleId>\n哈囉各位讀書會夥伴！如果不能參加 **[${eventTime.toLocalDate()}]** 讀書會的夥伴，請點擊按鈕請假喔")
                        .setAllowedMentions(listOf(MentionType.ROLE))
                        .addActionRow(Button.primary(customButtonId, "我要請假"))
                        .queue()
                }
        }
    }
}

fun createLeaveModalWhenLeaveButtonBeClicked(wsaDiscordProperties: WsaDiscordProperties) = listener {
    val wsaGaaSMemberRoleId = wsaDiscordProperties.wsaGaaSMemberRoleId
    on<ButtonInteractionEvent> {
        when {
            !(member!!.isGaaSMember(wsaGaaSMemberRoleId)) -> {
                reply("看起來你似乎不是 GaaS 讀書會成員喔，那就不用特別請假啦").setEphemeral(true).queue()
                return@on
            }
            now().isAfter(eventTime) -> {
                reply("超過可以請假的時間囉，下次請記得要在活動開始前請假喔").setEphemeral(true).queue()
                return@on
            }
        }

        takeIf { button.id == customButtonId }
            ?.run {
                val subject = TextInput.create("leave-reason", "請假事由", TextInputStyle.SHORT)
                    .setPlaceholder("Subject of this ticket")
                    .setMinLength(10)
                    .setMaxLength(100) // or setRequiredRange(10, 100)
                    .build()

                Modal.create(customModalId, "GaaS 讀書會請假條")
                    .addActionRow(subject)
                    .build()
                    .also { replyModal(it).queue() }
            }
    }
}

fun saveLeaveReasonWhenSubmit() = listener {
    on<ModalInteractionEvent> {
        takeIf { modalId == customModalId }
            ?.run {
                val filePath = createLeaveDataFile()
                val nickname = interaction.member!!.nickname
                val leaveReason = getValue("leave-reason")?.asString
                Files.writeString(filePath, "$nickname : $leaveReason${lineSeparator()}", APPEND)
                reply("哈囉，$nickname！我們收到你的請假申請囉，期待你下週能來和我們暢聊你的開發成果。").setEphemeral(true).queue()
            }

    }
}

private fun createLeaveDataFile(): Path {
    val fileName = DATABASE_FILENAME_TEMPLATE.replace("\$date", eventTime.toLocalDate().toString())
    File(DATABASE_DIRECTORY).createDirectoryIfNotExists()
    return File(DATABASE_DIRECTORY + fileName).createFileIfNotExists()
}

private fun Member.isGaaSMember(gaaSMemberRoleId: String): Boolean =
    gaaSMemberRoleId in roles.mapNotNull { it.id }