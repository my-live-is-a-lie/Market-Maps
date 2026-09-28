from pathlib import Path

p = Path("app/src/main/java/com/marketmaps/app/ui/map/SearchBar.kt")
src = p.read_text(encoding="utf-8")

# Imports
for imp in [
    "import androidx.compose.animation.core.animateFloatAsState",
    "import androidx.compose.animation.core.tween",
    "import androidx.compose.foundation.lazy.LazyRow",
    "import androidx.compose.foundation.lazy.itemsIndexed",
    "import androidx.compose.foundation.lazy.rememberLazyListState",
    "import androidx.compose.ui.graphics.graphicsLayer",
]:
    if imp not in src:
        src = src.replace(
            "import androidx.compose.foundation.lazy.items",
            "import androidx.compose.foundation.lazy.items\n" + imp,
            1,
        )

helper = '''
/** يطابق نص البحث مع اسم زر الفلتر السريع (تطبيع عربي + بادئة أو احتواء). */
private fun matchingQuickFilter(query: String, filters: List<String>): String? {
    val q = TextNormalizer.normalize(query)
    if (q.length < 1) return null
    val candidates = filters.filter { it != FILTER_ALL }
    candidates.firstOrNull { TextNormalizer.normalize(it).startsWith(q) }?.let { return it }
    return candidates.firstOrNull { TextNormalizer.normalize(it).contains(q) }
}

'''

if "matchingQuickFilter" not in src:
    src = src.replace(
        "@Composable\nfun SearchBar(",
        helper + "@Composable\nfun SearchBar(",
        1,
    )

old_row_start = "            Row(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .horizontalScroll(rememberScrollState())"
if old_row_start not in src:
    if "matchedFilter" in src and "LazyRow" in src:
        print("already patched")
        raise SystemExit(0)
    raise SystemExit("chip Row not found")

# Find end of the Row block: closing of forEach + closing of Row
start = src.index(old_row_start)
# Find matching close: after FilterChip block ends with "                }\n            }"
marker_end = "                    )\n                }\n            }\n\n            if (query.isNotBlank()"
end_rel = src.find(marker_end, start)
if end_rel < 0:
    raise SystemExit("end of chip row not found")
# keep from "            if (query..." 
end = end_rel  # replace up to but not including the if

new_row = '''            // تطابق نص البحث مع زر الفلتر السريع → تكبير 10٪ + تمرير للوسط
            val matchedFilter = remember(query, quickFilters) {
                matchingQuickFilter(query, quickFilters)
            }
            val chipsListState = rememberLazyListState()
            LaunchedEffect(matchedFilter, query, quickFilters) {
                if (matchedFilter != null) {
                    val idx = quickFilters.indexOf(matchedFilter)
                    if (idx >= 0) {
                        chipsListState.scrollToItem(idx)
                        val layoutInfo = chipsListState.layoutInfo
                        val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                        if (itemInfo != null && viewport > 0) {
                            val scrollOffset = -((viewport - itemInfo.size) / 2)
                            chipsListState.animateScrollToItem(idx, scrollOffset = scrollOffset)
                        } else {
                            chipsListState.animateScrollToItem(idx)
                        }
                    }
                } else if (query.isBlank()) {
                    chipsListState.animateScrollToItem(0)
                }
            }
            LazyRow(
                state = chipsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(quickFilters, key = { _, name -> name }) { _, name ->
                    val selected = if (showingSubs) {
                        if (name == FILTER_ALL) filterSub == FILTER_ALL else filterSub == name
                    } else {
                        filterType == name
                    }
                    val selectedColor = if (showingSubs) {
                        if (name == FILTER_ALL) typeColor
                        else Color(MarkerIconHelper.colorForCategory("$filterType $name"))
                    } else {
                        if (name == FILTER_ALL) MaterialTheme.colorScheme.primary
                        else Color(MarkerIconHelper.colorForCategory(name))
                    }
                    val isMatched = matchedFilter != null && name == matchedFilter
                    val chipScale by animateFloatAsState(
                        targetValue = if (isMatched) 1.1f else 1f,
                        animationSpec = tween(durationMillis = 220),
                        label = "chipScale"
                    )
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (showingSubs) {
                                if (name == FILTER_ALL) {
                                    onFilterTypeChange(FILTER_ALL)
                                } else {
                                    onFilterSubChange(name)
                                }
                            } else {
                                onFilterTypeChange(name)
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        },
                        label = {
                            Text(
                                name,
                                color = if (selected) Color.White else barContent
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = barColor,
                            labelColor = barContent,
                            selectedContainerColor = selectedColor,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = barContent.copy(alpha = 0.35f),
                            selectedBorderColor = selectedColor
                        )
                    )
                }
            }

'''

src = src[:start] + new_row + src[end:]
p.write_text(src, encoding="utf-8")
print("patched SearchBar.kt", len(src))
