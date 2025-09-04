package com.ms.helloworld.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedRoute: String?,
    onItemSelected: (String) -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val iconSize = screenWidth * 0.06f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomNavItem.items.forEach { item ->
            val selected = selectedRoute == item.route
            val iconRes = if (selected) item.selectedIconRes else item.iconRes

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onItemSelected(item.route)
                    }
                    .padding(vertical = screenWidth * 0.025f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = item.label,
                    modifier = Modifier.size(iconSize)
                )
                Text(
                    text = item.label,
                    color = if (selected) Color.Black else Color.Gray,
                    fontSize = screenWidth.value.times(0.03f).sp
                )
            }
        }
    }
}