package com.jingwen.voicediary.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingwen.voicediary.data.DiaryMediaEntity
import com.jingwen.voicediary.data.StickerAsset
import com.jingwen.voicediary.data.StickerCatalog
import com.jingwen.voicediary.ui.theme.Cream
import com.jingwen.voicediary.ui.theme.DeepRose
import com.jingwen.voicediary.ui.theme.MutedBrown
import com.jingwen.voicediary.ui.theme.Peach
import com.jingwen.voicediary.ui.theme.Rose

@Composable
fun DiarySticker(
    item: DiaryMediaEntity,
    imagePath: String,
    modifier: Modifier = Modifier,
    onRemove: ((DiaryMediaEntity) -> Unit)? = null
) {
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            Modifier.width(104.dp).height(96.dp).align(Alignment.CenterStart),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(), item.originalName,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                )
            } else {
                Text("表情", color = MutedBrown, fontSize = 12.sp)
            }
            onRemove?.let { remove ->
                SiteIconButton(
                    Icons.Outlined.Close,
                    "删除表情",
                    { remove(item) },
                    Modifier.align(Alignment.TopEnd).size(26.dp)
                        .background(Color.White.copy(alpha = .88f), RoundedCornerShape(13.dp)),
                    tint = Rose
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(onDismiss: () -> Unit, onSelect: (StickerAsset) -> Unit) {
    var selectedPackId by remember { mutableStateOf(StickerCatalog.packs.first().id) }
    val selectedPack = StickerCatalog.packs.first { it.id == selectedPackId }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("添加小表情", color = DeepRose, fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 4.dp))
            Text("点击后会插入光标位置，并自动回到文字输入。",
                color = MutedBrown, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StickerCatalog.packs.forEach { pack ->
                    val selected = pack.id == selectedPackId
                    Box(
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Rose else Peach.copy(alpha = .72f))
                            .clickable { selectedPackId = pack.id }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(pack.name, color = if (selected) Color.White else DeepRose, fontSize = 13.sp)
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(selectedPack.stickers, key = { it.id }) { sticker ->
                    StickerChoice(sticker, onSelect)
                }
            }
        }
    }
}

@Composable
private fun StickerChoice(sticker: StickerAsset, onSelect: (StickerAsset) -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(sticker.assetPath) {
        context.assets.open(sticker.assetPath).use(BitmapFactory::decodeStream)
    }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Peach.copy(alpha = .55f))
            .clickable { onSelect(sticker) }.padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), sticker.label, Modifier.size(68.dp), contentScale = ContentScale.Fit)
        }
        Text(sticker.label, color = MutedBrown, fontSize = 9.sp, maxLines = 1)
    }
}
