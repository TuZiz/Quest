package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** IO only. Update exact bundled introduction wording, keeping custom text and comments. */
object IntroductionMenuTextUpgrade {
    private val replacements=linkedMapOf(
        "actionbar" to ("  actionbar: '<#FFD700>✦ 当前目标：<#E0F7FA>右键大厅中的「引航员」交谈 <#7F8C8D>｜ <#C2E9FB>留意右侧任务栏提示'\n" to "  actionbar: '<#FFD700>✦ 当前目标：<#E0F7FA>右键大厅中的「引航员」打开空岛界面 <#7F8C8D>｜ <#C2E9FB>留意右侧任务栏提示'\n"),
        "objective" to ("  objective: '<#E0F7FA>与大厅引航员交谈'\n" to "  objective: '<#E0F7FA>与大厅引航员交互，打开空岛界面'\n"),
        "hints" to ("  hints:\n    - '<#C2E9FB>右键点击大厅中的 NPC「引航员」'\n    - '<#C2E9FB>查看屏幕右侧任务栏了解操作步骤'\n" to "  hints:\n    - '<#C2E9FB>右键点击大厅中的 NPC「引航员」，直接打开创建或加入空岛界面'\n    - '<#C2E9FB>创建或加入空岛后，继续右侧任务栏中的经营教学'\n"),
        "complete" to ("  complete: '<#A8FF78>✔ 引导交谈已完成！<#E0F7FA>请留意右侧任务栏，再次右键<#FFD700>引航员<#E0F7FA>即可打开空岛面板。'\n" to "  complete: '<#A8FF78>✔ 已认识引航员！<#E0F7FA>请在空岛界面创建或加入空岛，继续星海启航任务。'\n"),
        "interrupted" to ("  interrupted: '<#FFB75E>ℹ <#94A3B8>交谈已中断，当前进度已保留。再次右键引航员即可继续对话。'\n" to "  interrupted: '<#FFB75E>ℹ <#94A3B8>进度已保留，再次右键引航员即可打开空岛界面。'\n"),
        "no-dialogue" to ("  no-dialogue: '<#94A3B8>▪ 当前没有待确认的聊天引导，请先前往大厅右键引航员。'\n" to "  no-dialogue: '<#94A3B8>▪ 引航员现在直接打开空岛界面，无需对话确认。'\n"),
        "npc-unavailable" to ("  npc-unavailable: '<#C2E9FB>💡 请右键大厅中的引航员开始交谈；若 NPC 暂不可用，可点击 <click:run_command:''/quest guide skip''><hover:show_text:''<#FFD700>点击跳过引导''><#FFD700><underlined>/quest guide skip</underlined></hover></click> <#C2E9FB>直接跳过。'\n" to "  npc-unavailable: '<#C2E9FB>💡 请右键大厅中的引航员打开空岛界面，也可使用 <#FFD700>/quest guide replay<#C2E9FB>。'\n"),
        "help" to ("  help: |-\n    <#FFD700>✦ <gradient:#FFD700:#FDE68A><bold>新手引导指令帮助</bold></gradient>\n    <click:run_command:'/quest guide skip'><hover:show_text:'<#FFB75E>点击跳过引导'> <#FFD700>▪ <#E0F7FA>/quest guide skip <#94A3B8>- 跳过个人新手指引</hover></click>\n    <click:run_command:'/quest guide replay'><hover:show_text:'<#89F7FE>点击重温引航员对话'> <#FFD700>▪ <#E0F7FA>/quest guide replay <#94A3B8>- 重新播放引航员对话内容</hover></click>\n" to "  help: '<#FFD700>✦ 新手引导<newline><#E0F7FA>/quest guide replay <#94A3B8>- 打开空岛界面<newline><#E0F7FA>/quest guide skip <#94A3B8>- 跳过个人指引并打开空岛界面'\n")
    )
    fun migrate(root: Path) {
        val file=root.resolve("lang/zh_cn.yml")
        if(!Files.exists(file)) return
        val old=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(old) }
        require(y.get("schema-version")==1) { "$file:schema-version=${y.get("schema-version")} unsupported; original preserved" }
        var next=old.replace("\r\n","\n")
        for(wording in replacements.values) {
            // Match only the introduction section, never equally named fields elsewhere.
            val start=Regex("(?m)^introduction:[ \t]*(?:#.*)?$").find(next) ?: continue
            val tail=next.substring(start.range.last+1)
            val end=Regex("""(?m)^[^ \t#\r\n][^\r\n]*:""").find(tail)?.range?.first ?: tail.length
            val block=tail.substring(0,end)
            if(block.contains(wording.first)) next=next.substring(0,start.range.last+1)+block.replace(wording.first,wording.second)+tail.substring(end)
        }
        if(old.contains("\r\n")) next=next.replace("\n","\r\n")
        if(next==old) return
        YamlConfiguration().apply { loadFromString(next) }
        val backup=root.resolve(".migrations/introduction-menu-v1/lang.before")
        if(!Files.exists(backup)) { Files.createDirectories(backup.parent); Files.copy(file,backup) }
        ResourceUpgrade.replace(file,next)
    }
}
