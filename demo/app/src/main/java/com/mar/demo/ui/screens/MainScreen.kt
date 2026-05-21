package com.mar.demo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.theme.MarColors

enum class MarTab(val label: String) {
    ACTIVE("Active"),
    LIBRARY("Library"),
    EXECUTION("Run"),
    SETTINGS("Settings")
}

@Composable
fun MainScreen(
    activeContent: @Composable () -> Unit,
    libraryContent: @Composable () -> Unit,
    executionContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MarTab.entries

    Scaffold(
        containerColor = MarColors.BackgroundDark,
        bottomBar = {
            NavigationBar(
                containerColor = MarColors.SurfaceElevated,
                contentColor = MarColors.TextPrimary,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MarTab.ACTIVE -> Icons.Default.Bolt
                                    MarTab.LIBRARY -> Icons.Default.LibraryBooks
                                    MarTab.EXECUTION -> Icons.Default.Terminal
                                    MarTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MarColors.Blue.copy(alpha = 0.15f),
                            selectedIconColor = MarColors.Blue,
                            selectedTextColor = MarColors.Blue,
                            unselectedIconColor = MarColors.TextSecondary,
                            unselectedTextColor = MarColors.TextTertiary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabContent"
            ) { index ->
                when (tabs[index]) {
                    MarTab.ACTIVE -> activeContent()
                    MarTab.LIBRARY -> libraryContent()
                    MarTab.EXECUTION -> executionContent()
                    MarTab.SETTINGS -> settingsContent()
                }
            }
        }
    }
}
