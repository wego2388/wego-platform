package com.wego.mobile.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wego.mobile.shared.experience.ExperienceProfile

@Composable
@Suppress("FunctionName")
fun WegoOpsRoot(experienceProfile: ExperienceProfile = ExperienceProfile.STANDARD) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Wego Ops", style = MaterialTheme.typography.headlineMedium)
                Text("Shared infrastructure, staff-focused product experience")
                Text("Experience profile: ${experienceProfile.name}")
            }
        }
    }
}
