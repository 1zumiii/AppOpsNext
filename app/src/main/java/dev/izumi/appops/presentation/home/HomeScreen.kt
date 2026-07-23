package dev.izumi.appops.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.izumi.appops.R
import dev.izumi.appops.model.DeviceSummary
import dev.izumi.appops.presentation.components.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    device: DeviceSummary,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            StatusCard(
                title = stringResource(R.string.status_device_title),
                value = stringResource(
                    R.string.status_device_value,
                    device.manufacturer,
                    device.model,
                ),
                detail = stringResource(
                    R.string.status_device_detail,
                    device.androidVersion,
                    device.apiLevel,
                ),
            )
            StatusCard(
                title = stringResource(R.string.status_shizuku_title),
                value = stringResource(R.string.status_waiting),
                detail = stringResource(R.string.status_shizuku_pending_detail),
            )
            StatusCard(
                title = stringResource(R.string.status_appops_title),
                value = stringResource(R.string.status_disconnected),
                detail = stringResource(R.string.status_appops_disconnected_detail),
            )
        }
    }
}

