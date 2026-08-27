package com.codeaza.bhaiyaaa.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.util.PermissionGroup
import com.codeaza.bhaiyaaa.util.Permissions
import kotlinx.coroutines.launch

private data class OnboardingPage(val titleRes: Int, val bodyRes: Int)

private val PAGES = listOf(
    OnboardingPage(R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(R.string.onboarding_3_title, R.string.onboarding_3_body),
    OnboardingPage(R.string.onboarding_4_title, R.string.onboarding_4_body),
    OnboardingPage(R.string.onboarding_5_title, R.string.onboarding_5_body)
)

/**
 * First-run flow: five value pages, then permission setup.
 *
 * Permissions are requested here and only here at launch, each with its own
 * explanation, and every one is skippable - the brief forbids a first-launch
 * permission blast, and the app is designed to degrade rather than block when
 * something is declined.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onPermissionsChanged: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size + 1 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == PAGES.size

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            AnimatedVisibility(visible = !isLastPage, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(PAGES.size) }
                }) { Text(stringResource(R.string.onboarding_skip)) }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            if (page < PAGES.size) {
                ValuePage(PAGES[page], isFirst = page == 0)
            } else {
                PermissionSetupPage(onPermissionsChanged = onPermissionsChanged)
            }
        }

        // Page indicator
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(PAGES.size + 1) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                }) { Text(stringResource(R.string.onboarding_back)) }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(onClick = {
                if (isLastPage) onFinished()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }) {
                Text(
                    if (isLastPage) stringResource(R.string.onboarding_get_started)
                    else stringResource(R.string.onboarding_next)
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ValuePage(page: OnboardingPage, isFirst: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(page.titleRes),
            style = if (isFirst) MaterialTheme.typography.displaySmall
            else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionSetupPage(onPermissionsChanged: () -> Unit) {
    val context = LocalContext.current
    // Bumped after each result so the granted/not-granted rows recompose.
    var refreshToken by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshToken++
        onPermissionsChanged()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Each one is optional. BHAIYAAA works with whatever you allow, " +
                "and tells you what's missing rather than nagging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Permissions.CORE.forEach { group ->
            androidx.compose.runtime.key(refreshToken, group) {
                PermissionRow(
                    group = group,
                    granted = group.isGranted(context),
                    onGrant = {
                        if (group.permissions.isEmpty()) {
                            refreshToken++
                        } else {
                            launcher.launch(group.permissions.toTypedArray())
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "The microphone is asked for separately, only when you tap the mic " +
                "in Assistant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionRow(
    group: PermissionGroup,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(group.titleRes),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(group.whyRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.perm_granted),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(onClick = onGrant) {
                    Text(stringResource(R.string.perm_grant))
                }
            }
        }
    }
}
