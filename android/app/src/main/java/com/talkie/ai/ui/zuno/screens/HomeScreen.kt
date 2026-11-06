package com.talkie.ai.ui.zuno.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talkie.ai.ui.zuno.components.ZunoActionTopBar
import com.talkie.ai.ui.zuno.components.ZunoAgentHorizon
import com.talkie.ai.ui.zuno.components.ZunoTalkDock
import com.talkie.ai.ui.zuno.theme.ZunoColors
import com.talkie.ai.ui.zuno.theme.ZunoSpace
import com.talkie.ai.ui.zuno.theme.ZunoType

/** 5 · Home — Agent Horizon with greeting and the Talk Dock. */
@Composable
fun HomeScreen(
    firstName: String,
    languageLabel: String,
    onOpenDrawer: () -> Unit,
    onPersonalize: () -> Unit,
    onTalk: () -> Unit,
    onLessons: () -> Unit,
    onRoleplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ZunoColors.BgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ZunoActionTopBar(onMenu = onOpenDrawer, onAction = onPersonalize)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(372.dp)
                .padding(horizontal = ZunoSpace.s8)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Talk",
                    onClick = onTalk,
                ),
        ) {
            ZunoAgentHorizon(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 172.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZunoSpace.s10),
            ) {
                Text(
                    "Tap to talk · $languageLabel",
                    style = ZunoType.FootnoteStrong,
                    color = ZunoColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Hola $firstName, ¿qué practicamos hoy?",
                    style = ZunoType.Display,
                    color = ZunoColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(300.dp),
                )
            }
        }
        Spacer(Modifier.height(ZunoSpace.s24))
        ZunoTalkDock(
            onLessons = onLessons,
            onTalk = onTalk,
            onRoleplay = onRoleplay,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(18.dp))
    }
}
