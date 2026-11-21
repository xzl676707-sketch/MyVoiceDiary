package com.jingwen.voicediary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jingwen.voicediary.ui.screens.*
import com.jingwen.voicediary.ui.theme.Cream
import com.jingwen.voicediary.ui.theme.MyVoiceDiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MyVoiceDiaryTheme {
                Box(Modifier.fillMaxSize().background(Cream).safeDrawingPadding()) {
                    DiaryApp()
                }
            }
        }
    }
}

@Composable
private fun DiaryApp(viewModel: DiaryViewModel = viewModel()) {
    val navController = rememberNavController()
    val diaries by viewModel.diaries.collectAsStateWithLifecycle()
    val showSamples by viewModel.showSamples.collectAsStateWithLifecycle()
    val showTimes by viewModel.showTimes.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val computerAddress by viewModel.computerAddress.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            HomeScreen(diaries, showTimes,
                onOpenDiary = { navController.navigate("detail/" + it) },
                onNewDiary = { navController.navigate("editor") },
                onSearch = { navController.navigate("search") },
                onSettings = { navController.navigate("settings") })
        }
        composable("search") {
            SearchScreen(diaries, onBack = { navController.popBackStack() },
                onOpenDiary = { navController.navigate("detail/" + it) })
        }
        composable("settings") {
            MyScreen(
                realCount = diaries.count { it.id > 0 },
                sampleCount = diaries.count { it.id < 0 },
                showSamples = showSamples, showTimes = showTimes,
                onSamplesChange = viewModel::setShowSamples, onTimesChange = viewModel::setShowTimes,
                syncState = syncState,
                computerAddress = computerAddress,
                pairingCode = pairingCode,
                onComputerAddressChange = viewModel::setComputerAddress,
                onPairingCodeChange = viewModel::setPairingCode,
                onFindComputer = viewModel::findComputer,
                onBackupToComputer = viewModel::backupToComputer,
                onRestoreFromComputer = viewModel::restoreFromComputer,
                onClearSyncMessage = viewModel::clearSyncMessage,
                onBack = { navController.popBackStack() })
        }
        composable("detail/{diaryId}", arguments = listOf(navArgument("diaryId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("diaryId") ?: return@composable
            DetailScreen(id, diaries, viewModel, showTimes,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("editor?diaryId=" + it) },
                onOpenDiary = { nextId ->
                    navController.navigate("detail/" + nextId) {
                        // Replace the current detail so repeated paging does not grow the back stack.
                        popUpTo("detail/{diaryId}") { inclusive = true }
                        launchSingleTop = true
                    }
                })
        }
        composable("editor?diaryId={diaryId}", arguments = listOf(navArgument("diaryId") {
            type = NavType.LongType
            defaultValue = 0L
        })) { entry ->
            val id = entry.arguments?.getLong("diaryId")?.takeIf { it != 0L }
            val editor: EditorViewModel = viewModel(viewModelStoreOwner = entry)
            EditorScreen(id, viewModel, editor, onBack = { navController.popBackStack() })
        }
    }
}
