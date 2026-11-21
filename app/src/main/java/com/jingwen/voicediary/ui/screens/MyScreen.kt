package com.jingwen.voicediary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.SyncUiState
import com.jingwen.voicediary.ui.components.*
import com.jingwen.voicediary.ui.theme.*

private enum class SyncConfirmation { BACKUP_TO_PC, RESTORE_FROM_PC }

@Composable
fun MyScreen(
    realCount: Int,
    sampleCount: Int,
    showSamples: Boolean,
    showTimes: Boolean,
    onSamplesChange: (Boolean) -> Unit,
    onTimesChange: (Boolean) -> Unit,
    syncState: SyncUiState,
    computerAddress: String,
    pairingCode: String,
    onComputerAddressChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onFindComputer: () -> Unit,
    onBackupToComputer: () -> Unit,
    onRestoreFromComputer: () -> Unit,
    onClearSyncMessage: () -> Unit,
    onBack: () -> Unit
) {
    var confirmation by remember { mutableStateOf<SyncConfirmation?>(null) }
    val connectionReady = computerAddress.isNotBlank() && pairingCode.length == 6 && !syncState.running

    Column(Modifier.fillMaxSize().background(Cream)) {
        SiteTopBar("我的与设置", onBack, Icons.Outlined.ChevronLeft)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SitePanel {
                SettingsHeading("记录偏好")
                SettingRow("显示记录时间", "阅读日记时显示淡化的时间节点") {
                    DiarySwitch(showTimes, onTimesChange)
                }
                Text(
                    "现在使用手动文字输入；可以直接通过手机键盘添加 Emoji。",
                    fontSize = 12.sp, lineHeight = 19.sp, color = MutedBrown,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SitePanel {
                SettingsHeading("电脑同步")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryStat(realCount.toString(), "手机本地日记", Modifier.weight(1f))
                    DiaryStat(if (computerAddress.isBlank()) "未连接" else "已设置", "Windows 电脑", Modifier.weight(1f))
                }
                Text(
                    "先在电脑打开“我的日记电脑同步”，并让手机和电脑连接同一个 Wi-Fi。电脑只保存一个当前版本。",
                    Modifier.padding(top = 12.dp, bottom = 8.dp),
                    fontSize = 12.sp, lineHeight = 19.sp, color = MutedBrown
                )
                SiteButton(
                    if (syncState.running) "正在处理…" else "自动寻找电脑",
                    { if (!syncState.running) onFindComputer() },
                    filled = false
                )
                OutlinedTextField(
                    value = computerAddress,
                    onValueChange = onComputerAddressChange,
                    label = { Text("电脑地址") },
                    placeholder = { Text("例如 192.168.1.5:8765") },
                    singleLine = true,
                    enabled = !syncState.running,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = onPairingCodeChange,
                    label = { Text("电脑显示的 6 位配对码") },
                    singleLine = true,
                    enabled = !syncState.running,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SiteButton(
                        "备份到电脑",
                        { if (connectionReady) confirmation = SyncConfirmation.BACKUP_TO_PC },
                        filled = true
                    )
                    SiteButton(
                        "从电脑恢复",
                        { if (connectionReady) confirmation = SyncConfirmation.RESTORE_FROM_PC }
                    )
                }
                if (!connectionReady && !syncState.running) {
                    Text(
                        "填写电脑地址和 6 位配对码后即可同步。",
                        fontSize = 12.sp, color = MutedBrown, modifier = Modifier.padding(top = 9.dp)
                    )
                }
                SyncMessage(syncState, onClearSyncMessage)
            }

            SitePanel {
                SettingsHeading("同步规则")
                SyncRule("备份到电脑", "以手机为准，电脑原来的当前备份会被替换。")
                SyncRule("从电脑恢复", "以电脑为准，手机多出的日记、图片和视频会被删除。")
                Text(
                    "只有你主动点击按钮才会同步；平时电脑不用开机，也不会上传到云端。",
                    fontSize = 12.sp, lineHeight = 19.sp, color = MutedBrown,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SitePanel {
                SettingsHeading("界面预览")
                SettingRow("显示示例日记", "示例与真实日记分开，关闭后即可隐藏。") {
                    DiarySwitch(showSamples, onSamplesChange)
                }
                Text(
                    "当前展示 $sampleCount 篇示例。编辑会保存回本篇，关闭后保留修改。",
                    fontSize = 12.sp, lineHeight = 19.sp, color = MutedBrown
                )
            }
            Text(
                "我的日记本 · V0.9\n本地保存，需要时再同步到自己的电脑 ♡",
                fontSize = 12.sp, lineHeight = 22.sp, color = MutedBrown,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    confirmation?.let { action ->
        val restoring = action == SyncConfirmation.RESTORE_FROM_PC
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (restoring) "确认从电脑恢复？" else "确认备份到电脑？") },
            text = {
                Text(
                    if (restoring) {
                        "将以电脑备份为准。手机里多出的日记、图片和视频会被删除；如果恢复失败，手机原有内容不会改变。"
                    } else {
                        "将以手机内容为准，替换电脑中原来的当前备份。电脑不会另外保留历史版本。"
                    },
                    fontSize = 13.sp, lineHeight = 21.sp,
                    color = if (restoring) Color(0xFF9B3F4A) else WarmBrown
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (restoring) onRestoreFromComputer() else onBackupToComputer()
                }) { Text(if (restoring) "确认覆盖手机" else "确认替换电脑备份") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SyncMessage(state: SyncUiState, onClear: () -> Unit) {
    state.message?.let { message ->
        Surface(
            color = if (state.isError) Color(0xFFFFE9E9) else Peach,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Row(
                Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Rose)
                }
                Text(
                    message, modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp,
                    color = if (state.isError) Color(0xFF9B3F4A) else WarmBrown
                )
                if (!state.running) TextButton(onClick = onClear) { Text("知道了") }
            }
        }
    }
}

@Composable
private fun SyncRule(title: String, text: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, fontSize = 14.sp, color = WarmBrown)
        Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = MutedBrown, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SettingsHeading(title: String) {
    Text(title, fontSize = 18.sp, color = DeepRose, fontFamily = FontFamily.Serif,
        modifier = Modifier.padding(bottom = 13.dp))
}

@Composable
private fun SettingRow(title: String, subtitle: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = WarmBrown)
            Text(subtitle, fontSize = 12.sp, lineHeight = 18.sp, color = MutedBrown,
                modifier = Modifier.padding(top = 4.dp))
        }
        content()
    }
}

@Composable
private fun DiarySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(
        checkedTrackColor = Rose, checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFFDFD3D4), uncheckedThumbColor = Color.White,
        uncheckedBorderColor = Color.Transparent))
}

@Composable
private fun DiaryStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Peach).padding(12.dp)) {
        Text(value, color = DeepRose, fontSize = 18.sp)
        Text(label, color = MutedBrown, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
