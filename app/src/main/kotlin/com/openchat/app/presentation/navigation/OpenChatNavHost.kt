package com.openchat.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openchat.app.presentation.theme.Motion
import com.openchat.app.presentation.screens.auth.LoginScreen
import com.openchat.app.presentation.screens.auth.ProfileSetupScreen
import com.openchat.app.presentation.screens.chat.ChatScreen
import com.openchat.app.presentation.screens.contacts.ContactsScreen
import com.openchat.app.presentation.screens.home.HomeScreen
import com.openchat.app.presentation.screens.settings.SettingsScreen
import com.openchat.app.presentation.screens.settings.NotificationsScreen
import com.openchat.app.presentation.screens.settings.PrivacyScreen
import com.openchat.app.presentation.screens.settings.StorageScreen
import com.openchat.app.presentation.screens.settings.HelpScreen
import com.openchat.app.presentation.screens.settings.AboutScreen
import com.openchat.app.presentation.screens.settings.CallSettingsScreen
import com.openchat.app.presentation.screens.profile.ProfileScreen
import com.openchat.app.presentation.screens.stories.StoriesScreen
import com.openchat.app.presentation.screens.splash.SplashScreen
import com.openchat.app.presentation.screens.group.CreateGroupScreen
import com.openchat.app.presentation.screens.chat.ChatSettingsScreen
import com.openchat.app.presentation.screens.media.SharedMediaScreen
import com.openchat.app.presentation.screens.scheduled.ScheduledMessagesScreen
import com.openchat.app.presentation.screens.call.CallScreen
import com.openchat.app.presentation.screens.call.CallType
import com.openchat.app.presentation.screens.lock.LockScreen
import com.openchat.app.presentation.screens.settings.AppLockSetupScreen
import com.openchat.app.presentation.screens.settings.ChatLockSetupScreen

object Routes {
    const val SPLASH = "splash"
    const val PROFILE_SETUP = "profile_setup"
    const val LOGIN = "login"
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val STORIES = "stories"
    const val CREATE_GROUP = "create_group"
    const val CHAT_SETTINGS = "chat_settings/{chatId}"
    const val SCHEDULED_MESSAGES = "scheduled_messages"
    const val SHARED_MEDIA = "shared_media/{chatId}"
    const val CALL = "call/{userId}/{isVideo}/{isIncoming}/{callId}"
    const val LOCK = "lock"
    const val LOCK_PRIVATE = "lock_private"
    const val APP_LOCK_SETUP = "app_lock_setup"
    const val CHAT_LOCK_SETUP = "chat_lock_setup"
    const val NOTIFICATIONS = "notifications"
    const val CALL_SETTINGS = "call_settings"
    const val PRIVACY = "privacy"
    const val STORAGE = "storage"
    const val HELP = "help"
    const val ABOUT = "about"

    fun chat(chatId: String) = "chat/$chatId"
    fun chatSettings(chatId: String) = "chat_settings/$chatId"
    fun sharedMedia(chatId: String) = "shared_media/$chatId"
    fun call(userId: String, isVideo: Boolean, isIncoming: Boolean = false, callId: String = "none") =
        "call/$userId/$isVideo/$isIncoming/$callId"
}

/**
 * Routes presented as full-screen modals: they rise from the bottom edge and
 * dismiss back into it instead of sliding horizontally like a pushed screen.
 * Matched on the destination *pattern* so `call/...` resolves for any argument set.
 */
private val ModalRoutes = setOf(
    Routes.CALL,
    Routes.LOCK,
    Routes.LOCK_PRIVATE,
    Routes.CREATE_GROUP
)

private fun String?.isModal(): Boolean =
    this != null && ModalRoutes.any { it == this }

@Composable
fun OpenChatNavHost(
    navController: NavHostController = rememberNavController(),
    pendingCallId: String? = null,
    pendingCallerId: String? = null,
    pendingIsVideo: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // The current back stack entry, observed so the transition lambdas below
    // re-evaluate whenever the destination changes.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // Transitions are resolved per destination *pattern*, so `chat/{chatId}`
        // correctly matches regardless of which chat id is on the stack.
        enterTransition = { if (currentRoute.isModal()) Motion.enterModal() else Motion.enterForward() },
        exitTransition = { if (currentRoute.isModal()) Motion.exitModal() else Motion.exitForward() },
        popEnterTransition = { if (currentRoute.isModal()) Motion.enterModalBack() else Motion.enterBack() },
        popExitTransition = { if (currentRoute.isModal()) Motion.exitModalBack() else Motion.exitBack() }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProfileSetup = {
                    navController.navigate(Routes.PROFILE_SETUP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            val consumedPendingCall = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (!consumedPendingCall.value && pendingCallId != null && pendingCallerId != null) {
                    consumedPendingCall.value = true
                    navController.navigate(Routes.call(pendingCallerId, pendingIsVideo, true, pendingCallId))
                }
            }

            HomeScreen(
                onNavigateToChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onNavigateToContacts = {
                    navController.navigate(Routes.CONTACTS)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Routes.CREATE_GROUP)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                },
                onNavigateToStories = {
                    navController.navigate(Routes.STORIES)
                },
                onNavigateToCall = { userId, isVideo ->
                    navController.navigate(Routes.call(userId, isVideo, false))
                },
                onIncomingCall = { userId, callId, isVideo ->
                    navController.navigate(Routes.call(userId, isVideo, true, callId))
                },
                onSignOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLockPrivate = {
                    navController.navigate(Routes.LOCK_PRIVATE)
                }
            )
        }

        composable(Routes.CHAT) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToProfile = { userId ->
                    navController.navigate(Routes.PROFILE)
                },
                onNavigateToChatSettings = { chatId ->
                    navController.navigate(Routes.chatSettings(chatId))
                },
                onNavigateToCall = { userId, isVideo ->
                    navController.navigate(Routes.call(userId, isVideo, false))
                }
            )
        }

        composable(Routes.CONTACTS) {
            ContactsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                // Receives a real chatId: ContactsViewModel resolves the contact's
                // uid into a chat via getOrCreateChat before emitting the effect.
                onNavigateToChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onInviteClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("smsto:")
                        putExtra("sms_body", "Hey! Join me on OpenChat - download it here: https://openchat.app/download")
                    }
                    try { context.startActivity(intent) } catch (e: Exception) {
                        android.util.Log.e("OpenChatNavHost", "Failed to launch SMS app", e)
                    }
                },
                onCallClick = { userId, isVideo ->
                    navController.navigate(Routes.call(userId, isVideo, false))
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                },
                onNavigateToNotifications = {
                    navController.navigate(Routes.NOTIFICATIONS)
                },
                onNavigateToPrivacy = {
                    navController.navigate(Routes.PRIVACY)
                },
                onNavigateToAppLock = {
                    navController.navigate(Routes.APP_LOCK_SETUP)
                },
                onNavigateToChatLock = {
                    navController.navigate(Routes.CHAT_LOCK_SETUP)
                },
                onNavigateToStorage = {
                    navController.navigate(Routes.STORAGE)
                },
                onNavigateToHelp = {
                    navController.navigate(Routes.HELP)
                },
                onNavigateToAbout = {
                    navController.navigate(Routes.ABOUT)
                },
                onNavigateToScheduledMessages = {
                    navController.navigate(Routes.SCHEDULED_MESSAGES)
                },
                onSignOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.STORIES) {
            StoriesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.CREATE_GROUP) {
            CreateGroupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGroupCreated = { chatId ->
                    navController.popBackStack()
                    navController.navigate(Routes.chat(chatId))
                }
            )
        }

        composable(Routes.CHAT_SETTINGS) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatSettingsScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSharedMedia = {
                    navController.navigate(Routes.sharedMedia(chatId))
                }
            )
        }

        composable(
            Routes.SHARED_MEDIA,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            SharedMediaScreen(
                chatId = chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMessage = { navController.popBackStack() }
            )
        }

        composable(Routes.SCHEDULED_MESSAGES) {
            ScheduledMessagesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { chatId ->
                    navController.popBackStack()
                    navController.navigate(Routes.chat(chatId))
                }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCallSettings = { navController.navigate(Routes.CALL_SETTINGS) }
            )
        }

        composable(Routes.CALL_SETTINGS) {
            CallSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAppLock = { navController.navigate(Routes.APP_LOCK_SETUP) },
                onNavigateToChatLock = { navController.navigate(Routes.CHAT_LOCK_SETUP) }
            )
        }

        composable(Routes.STORAGE) {
            StorageScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HELP) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.CALL,
            arguments = listOf(
                navArgument("callId") {
                    type = NavType.StringType
                    defaultValue = "none"
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val isVideo = backStackEntry.arguments?.getString("isVideo")?.toBoolean() ?: false
            val isIncoming = backStackEntry.arguments?.getString("isIncoming")?.toBoolean() ?: false
            val callIdRaw = backStackEntry.arguments?.getString("callId") ?: "none"
            val callId = if (callIdRaw == "none") "" else callIdRaw

            val callViewModel: com.openchat.app.presentation.screens.call.CallViewModel = hiltViewModel()
            val uiState by callViewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                if (!isIncoming && uiState.callState == com.openchat.app.presentation.screens.call.CallState.IDLE) {
                    callViewModel.onEvent(
                        com.openchat.app.presentation.screens.call.CallEvent.InitiateCall(
                            userId = userId,
                            isVideo = isVideo,
                            userName = "User"
                        )
                    )
                } else if (isIncoming && callId.isNotEmpty() && uiState.callId.isEmpty()) {
                    callViewModel.onEvent(
                        com.openchat.app.presentation.screens.call.CallEvent.IncomingCallDetected(
                            callerId = userId,
                            callId = callId,
                            isVideo = isVideo
                        )
                    )
                }
            }

            // Handle call effects
            LaunchedEffect(Unit) {
                callViewModel.effect.collect { effect ->
                    when (effect) {
                        is com.openchat.app.presentation.screens.call.CallEffect.CallEnded,
                        is com.openchat.app.presentation.screens.call.CallEffect.CallDeclined,
                        is com.openchat.app.presentation.screens.call.CallEffect.CallFailed -> {
                            navController.popBackStack()
                        }
                        else -> {}
                    }
                }
            }

            CallScreen(
                contactName = uiState.remoteUserName.ifEmpty { "User" },
                contactPhoto = null,
                isIncoming = isIncoming,
                callType = if (isVideo) CallType.VIDEO else CallType.VOICE,
                onAccept = {
                    val state = callViewModel.uiState.value
                    callViewModel.onEvent(
                        com.openchat.app.presentation.screens.call.CallEvent.AcceptCall(
                            callerId = state.remoteUserId.ifEmpty { userId },
                            callId = state.callId.ifEmpty { callId }
                        )
                    )
                },
                onDecline = {
                    callViewModel.onEvent(com.openchat.app.presentation.screens.call.CallEvent.DeclineCall)
                },
                onEnd = {
                    callViewModel.onEvent(com.openchat.app.presentation.screens.call.CallEvent.EndCall)
                },
                onToggleMute = {
                    callViewModel.onEvent(com.openchat.app.presentation.screens.call.CallEvent.ToggleMute)
                },
                onToggleSpeaker = {
                    callViewModel.onEvent(com.openchat.app.presentation.screens.call.CallEvent.ToggleSpeaker)
                },
                onSwitchCamera = {
                    callViewModel.onEvent(com.openchat.app.presentation.screens.call.CallEvent.SwitchCamera)
                },
                onToggleScreenShare = { mediaProjection ->
                    callViewModel.onEvent(com.openchat.app.presentation.screens.call.CallEvent.ToggleScreenShare(mediaProjection))
                },
                viewModel = callViewModel
            )
        }

        composable(Routes.LOCK) {
            LockScreen(
                targetMode = "standard",
                onAuthenticatedPrimary = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                },
                onAuthenticatedSecondary = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.LOCK_PRIVATE) {
            LockScreen(
                targetMode = "private",
                onAuthenticatedPrimary = {
                    navController.popBackStack()
                },
                onAuthenticatedSecondary = {
                    navController.popBackStack()
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.APP_LOCK_SETUP) {
            AppLockSetupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT_LOCK_SETUP) {
            ChatLockSetupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

    }
}