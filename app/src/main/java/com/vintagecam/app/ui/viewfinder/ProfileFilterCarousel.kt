package com.vintagecam.app.ui.viewfinder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vintagecam.app.ui.theme.VintageCamTypography
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.ComputationalMode
import com.vintagecam.profiles.Era
import kotlinx.coroutines.launch

@Composable
internal fun ProfileFilterCarousel(
    profiles: List<CameraProfile>,
    selectedIndex: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onProfileSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex) {
        if (expanded) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    if (!expanded) {
        CompactFilterDock(
            profiles = profiles,
            selectedIndex = selectedIndex,
            onExpand = { onExpandedChange(true) },
            onProfileSelected = onProfileSelected,
            modifier = modifier,
        )
        return
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .height(126.dp)
            .background(Color.Black.copy(alpha = 0.42f)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = true,
    ) {
        itemsIndexed(profiles) { index, profile ->
            FilterCarouselItem(
                profile = profile,
                isSelected = index == selectedIndex,
                onClick = {
                    scope.launch {
                        onProfileSelected(index)
                    }
                },
            )
        }
    }
}

@Composable
private fun CompactFilterDock(
    profiles: List<CameraProfile>,
    selectedIndex: Int,
    onExpand: () -> Unit,
    onProfileSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty()) return

    val selected = profiles[selectedIndex.coerceIn(profiles.indices)]
    val previousIndex = if (selectedIndex <= 0) profiles.lastIndex else selectedIndex - 1
    val nextIndex = if (selectedIndex >= profiles.lastIndex) 0 else selectedIndex + 1

    Row(
        modifier = modifier
            .height(58.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSideChip(
            text = shortName(profiles[previousIndex]),
            profile = profiles[previousIndex],
            onClick = { onProfileSelected(previousIndex) },
        )

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .width(184.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color.Black.copy(alpha = 0.46f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(25.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                ProfileEraIndicator(selected, isSelected = true)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = shortName(selected),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (selected.usesComputationalCapture()) {
                        Text(
                            text = "HQ${selected.burstFrameCount}",
                            color = Color(0xFFBCEBFF),
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF15657A).copy(alpha = 0.58f))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = selected.deviceLabel.uppercase(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        CompactSideChip(
            text = shortName(profiles[nextIndex]),
            profile = profiles[nextIndex],
            onClick = { onProfileSelected(nextIndex) },
        )
    }
}

@Composable
private fun CompactSideChip(
    text: String,
    profile: CameraProfile,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = profileAccent(profile).copy(alpha = 0.82f),
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(68.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 12.dp),
    )
}

@Composable
private fun FilterCarouselItem(
    profile: CameraProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) profileAccent(profile).copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.22f),
        label = "carousel-item-bg",
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.14f),
        label = "carousel-item-border",
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 0.96f,
        label = "carousel-item-scale",
    )

    val profileFont = when (profile.era) {
        Era.EIGHTIES -> VintageCamTypography.vhsFont
        Era.NINETIES -> VintageCamTypography.filmFont
        Era.TWO_THOUSANDS -> VintageCamTypography.digitalFont
    }

    Column(
        modifier = Modifier
            .width(126.dp)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ProfileEraIndicator(profile, isSelected)

        Text(
            text = shortName(profile),
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = if (isSelected) 11.sp else 10.sp,
            fontFamily = profileFont,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = profile.deviceLabel.uppercase(),
            color = Color.White.copy(alpha = if (isSelected) 0.56f else 0.38f),
            fontSize = 7.5.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (profile.usesComputationalCapture()) {
                FilterPill("HQ ${profile.burstFrameCount}F", Color(0xFF28B8D8).copy(alpha = 0.30f))
            }
            FilterPill(profile.categoryLabel, Color.White.copy(alpha = 0.18f))
            if (profile.tierLabel != "FREE") {
                FilterPill(profile.tierLabel, tierColor(profile).copy(alpha = 0.28f))
            } else if (profile.assetStatusLabel != "AVAILABLE") {
                FilterPill("PROTO", Color(0xFFFFD45A).copy(alpha = 0.24f))
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, color: Color) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.72f),
        fontSize = 6.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier
            .padding(top = 3.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun ProfileEraIndicator(profile: CameraProfile, isSelected: Boolean) {
    val indicatorColor = profileAccent(profile)
    val label = when (profile.id) {
        "vhs_1985" -> "VHS"
        "disposable_1998" -> "ISO"
        "digicam_2003" -> "CCD"
        "polaroid_1990" -> "600"
        "super8_2020" -> "S8"
        "cinestill_800t" -> "800T"
        "trix_400", "ilford_hp5" -> "BW"
        "cyanotype" -> "CY"
        else -> ""
    }

    Box(
        modifier = Modifier
            .size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            when (profile.id) {
                "vhs_1985" -> {
                    drawRoundRect(
                        color = indicatorColor.copy(alpha = 0.26f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = size.minDimension * 0.24f,
                        center = Offset(size.width * 0.28f, size.height * 0.5f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = size.minDimension * 0.24f,
                        center = Offset(size.width * 0.72f, size.height * 0.5f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                "disposable_1998" -> {
                    drawCircle(indicatorColor.copy(alpha = 0.24f), radius = size.minDimension * 0.48f)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.44f),
                        radius = size.minDimension * 0.18f,
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.5f),
                        startAngle = -40f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                        size = Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                "polaroid_1990" -> {
                    drawRoundRect(
                        color = Color(0xFFF4F0DD).copy(alpha = 0.86f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                    drawRect(
                        color = indicatorColor.copy(alpha = 0.32f),
                        topLeft = Offset(size.width * 0.18f, size.height * 0.14f),
                        size = Size(size.width * 0.64f, size.height * 0.52f),
                    )
                }
                "super8_2020" -> {
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.45f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    )
                    drawRect(
                        color = indicatorColor.copy(alpha = 0.30f),
                        topLeft = Offset(size.width * 0.22f, size.height * 0.12f),
                        size = Size(size.width * 0.56f, size.height * 0.76f),
                    )
                    repeat(4) { i ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.42f),
                            radius = 2.dp.toPx(),
                            center = Offset(size.width * 0.12f, size.height * (0.20f + i * 0.18f)),
                        )
                    }
                }
                else -> {
                    drawRoundRect(
                        color = indicatorColor.copy(alpha = 0.24f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(size.width * 0.20f, size.height * 0.22f),
                        size = Size(size.width * 0.60f, size.height * 0.42f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.42f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(size.width * 0.72f, size.height * 0.78f),
                    )
                }
            }

            if (isSelected) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.86f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(size.width - 4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Text(
            text = label,
            color = indicatorColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun profileAccent(profile: CameraProfile): Color = when (profile.era) {
    Era.EIGHTIES -> Color(0xFFFF4A4A)
    Era.NINETIES -> Color(0xFFFFD45A)
    Era.TWO_THOUSANDS -> Color(0xFF66F0A0)
}

private fun tierColor(profile: CameraProfile): Color = when (profile.tierLabel) {
    "SECRET" -> Color(0xFFFF4A9E)
    "PRO" -> Color(0xFFFFD45A)
    else -> Color.White
}

private fun shortName(profile: CameraProfile): String = when (profile.id) {
    "vhs_1985" -> "TAPE 85"
    "disposable_1998" -> "FUNSAVER"
    "digicam_2003" -> "CYBERSHOT"
    "polaroid_1990" -> "POLAROID"
    "super8_2020" -> "SUPER-8"
    "cinestill_800t" -> "800T"
    else -> profile.displayName.uppercase()
}

private fun CameraProfile.usesComputationalCapture(): Boolean {
    return computationalMode != ComputationalMode.SINGLE && burstFrameCount > 1
}
