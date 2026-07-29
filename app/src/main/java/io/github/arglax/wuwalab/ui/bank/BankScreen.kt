package io.github.arglax.wuwalab.ui.bank

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.BANK_EARLY_PENALTY_PERCENT
import io.github.arglax.wuwalab.data.BANK_GRACE_HOURS
import io.github.arglax.wuwalab.data.BANK_MAX_OPEN_DEPOSITS
import io.github.arglax.wuwalab.data.BANK_MIN_DEPOSIT
import io.github.arglax.wuwalab.data.BANK_PENALTY_END_HOURS
import io.github.arglax.wuwalab.data.BankMath
import io.github.arglax.wuwalab.data.BankOpenResult
import io.github.arglax.wuwalab.data.BankRepository
import io.github.arglax.wuwalab.data.Deposit
import io.github.arglax.wuwalab.data.DepositTerm
import io.github.arglax.wuwalab.data.EarlyExitBand
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.components.pulsingGlow
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.util.formatArgstrites
import io.github.arglax.wuwalab.util.rememberConfirmFeedback
import io.github.arglax.wuwalab.util.rememberTapFeedback
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
private fun fmtRate(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

private fun fmtDuration(ms: Long): String {
    if (ms <= 0L) return "ready"
    val days = ms / 86_400_000L
    val hours = (ms % 86_400_000L) / 3_600_000L
    val minutes = (ms % 3_600_000L) / 60_000L
    val seconds = (ms % 60_000L) / 1000L
    return when {
        days > 0 -> days.toString() + "d " + hours + "h"
        hours > 0 -> hours.toString() + "h " + minutes + "m"
        minutes > 0 -> minutes.toString() + "m " + seconds + "s"
        else -> seconds.toString() + "s"
    }
}

/**
 * The Investment Center.
 *
 * Argstrites go in, sit locked for a fixed term, and come back with interest.
 * Three things this screen refuses to be vague about, because every one of
 * them is a place a player could reasonably feel cheated:
 *
 *  1. The payout breakdown is always on screen, line by line, including the
 *     bonus percent and the additive multiplier - those are usually the
 *     biggest part of the number and hiding them makes the Bank look broken.
 *  2. Bonuses are read at CLAIM time, not deposit time, so the quote is
 *     labelled an estimate and says why it can move.
 *  3. Breaking a deposit early states the exact figure you get back and the
 *     exact penalty, before you confirm - never after.
 */
@Composable
fun BankScreen(
    bankRepo: BankRepository,
    wuwaRepo: WuwaRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val tapFeedback = rememberTapFeedback()
    val confirmFeedback = rememberConfirmFeedback()

    val balance by wuwaRepo.radiantAstriteFlow.collectAsState(initial = 0)
    val bonusPercent by wuwaRepo.bonusPercentFlow.collectAsState(initial = 0f)
    val multiplier by wuwaRepo.totalMultiplierFlow.collectAsState(initial = 1f)
    val openDeposits by bankRepo.openDepositsFlow.collectAsState(initial = emptyList())
    val closedDeposits by bankRepo.closedDepositsFlow.collectAsState(initial = emptyList())
    val locked by bankRepo.lockedTotalFlow.collectAsState(initial = 0)
    val lifetimeInterest by bankRepo.lifetimeInterestFlow.collectAsState(initial = 0)

    var amountText by remember { mutableStateOf("") }
    var selectedTerm by remember { mutableStateOf(DepositTerm.THREE_DAYS) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var pendingEarlyExit by remember { mutableStateOf<Deposit?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    // One-second tick so the countdowns and the grace-window warning stay live
    // rather than freezing at first composition.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            nowTick = System.currentTimeMillis()
        }
    }

    val amount = amountText.toIntOrNull() ?: 0
    val quote = remember(amount, selectedTerm, bonusPercent, multiplier) {
        BankMath.quote(amount.coerceAtLeast(0), selectedTerm, bonusPercent, multiplier)
    }
    val canAfford = amount in 1..balance
    val meetsMinimum = amount >= BANK_MIN_DEPOSIT
    val hasRoom = openDeposits.size < BANK_MAX_OPEN_DEPOSITS

    pendingEarlyExit?.let { deposit ->
        val held = deposit.millisHeld(nowTick)
        val band = BankMath.earlyExitBand(held)
        val penalty = BankMath.earlyPenalty(deposit.principal, band)
        val returned = deposit.principal - penalty
        AlertDialog(
            onDismissRequest = { pendingEarlyExit = null },
            title = { Text("Break this deposit early?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "It still has " + fmtDuration(deposit.millisRemaining(nowTick)) + " to run, so it pays no interest.",
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    QuoteRow("Principal", formatArgstrites(deposit.principal), TextPrimary)
                    if (penalty > 0) {
                        QuoteRow("Penalty (" + BANK_EARLY_PENALTY_PERCENT + "%)", "-" + formatArgstrites(penalty), CoralGlow)
                    }
                    QuoteRow("Interest", "0", TextMuted)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                    Spacer(Modifier.height(6.dp))
                    QuoteRow("You get back", formatArgstrites(returned), if (penalty > 0) CoralGlow else EmeraldGlow, bold = true)
                    if (band == EarlyExitBand.GRACE) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "You are still inside the " + BANK_GRACE_HOURS + "-hour free window, so this costs you nothing.",
                            color = EmeraldGlow,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                    if (band == EarlyExitBand.PENALTY) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Waiting " + fmtDuration(BankMath.millisUntilPenaltyEnds(held)) + " drops the penalty back to zero.",
                            color = AmberGlow,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = deposit
                    pendingEarlyExit = null
                    scope.launch {
                        val result = bankRepo.close(target.id)
                        if (result != null) {
                            confirmFeedback()
                            messageIsError = result.penaltyPaid > 0
                            message = "Returned " + formatArgstrites(result.totalReturned) + " Argstrites" +
                                (if (result.penaltyPaid > 0) " after a " + formatArgstrites(result.penaltyPaid) + " penalty." else " with no penalty.")
                        }
                    }
                }) { Text("Break it", color = CoralGlow) }
            },
            dismissButton = {
                TextButton(onClick = { pendingEarlyExit = null }) { Text("Keep it locked") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Investment Center",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HelpButton(
                    title = "Investment Center Help",
                    body = "Lock Argstrites for a fixed term and they come back with interest. " +
                        "Minimum deposit is " + BANK_MIN_DEPOSIT + " Argstrites, and you can have " +
                        BANK_MAX_OPEN_DEPOSITS + " deposits running at once.\n\n" +
                        "RATES\n" +
                        "1 Day 2.5% - 3 Days 6% - 1 Week 12% - 2 Weeks 20% - 1 Month 36%\n\n" +
                        "Longer terms pay more per deposit, but LESS per day. That is deliberate: " +
                        "ten 3-day deposits across a month return 60%, against the 36% one " +
                        "1-month deposit pays. Coming back every day or three genuinely beats " +
                        "parking everything once and walking away.\n\n" +
                        "BREAKING A DEPOSIT EARLY\n" +
                        "Under " + BANK_GRACE_HOURS + "h: full principal back, no interest, no penalty.\n" +
                        BANK_GRACE_HOURS + "h to " + BANK_PENALTY_END_HOURS + "h: " + BANK_EARLY_PENALTY_PERCENT +
                        "% of the principal is kept, and no interest.\n" +
                        "After " + BANK_PENALTY_END_HOURS + "h: full principal back, no interest, no penalty.\n\n" +
                        "Your Bonus % and multiplier are applied to the INTEREST at the moment you " +
                        "claim, not when you deposit - so buying a title mid-term makes your payout " +
                        "bigger. The principal itself is never bonused; it is your own money coming back."
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_radiant_astrite),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Available", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        Text(
                            formatArgstrites(balance),
                            color = VioletGlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Locked", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        Text(formatArgstrites(locked), color = CyanGlow, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Interest earned all-time",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "+" + formatArgstrites(lifetimeInterest),
                        color = EmeraldGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = AmberGlow) {
                Text("New deposit", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Amount in Argstrites") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 100).forEach { pct ->
                        SmallPill(
                            label = if (pct == 100) "Max" else pct.toString() + "%",
                            onClick = {
                                tapFeedback()
                                amountText = (balance.toLong() * pct / 100).toInt().toString()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Term", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DepositTerm.entries.forEach { term ->
                        TermPill(
                            term = term,
                            selected = term == selectedTerm,
                            onClick = { tapFeedback(); selectedTerm = term }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    fmtRate(selectedTerm.ratePerDay) + "% per day - shorter terms pay more per day, so repeating them beats one long lock.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(10.dp))

                Text("Payout at maturity", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.labelLarge.fontSize)
                Spacer(Modifier.height(8.dp))
                QuoteRow("Principal", formatArgstrites(quote.principal), TextPrimary)
                QuoteRow(
                    "Interest at " + fmtRate(selectedTerm.ratePercent) + "%",
                    "+" + formatArgstrites(quote.baseInterest),
                    TextSecondary
                )
                if (bonusPercent > 0f) {
                    QuoteRow(
                        "Bonus +" + fmtRate(bonusPercent) + "%",
                        formatArgstrites(quote.interestAfterPercent),
                        CyanGlow
                    )
                }
                if (multiplier > 1f) {
                    QuoteRow(
                        "Multiplier \u00d7" + fmtRate(multiplier),
                        formatArgstrites(quote.finalInterest),
                        AmberGlow
                    )
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                Spacer(Modifier.height(6.dp))
                QuoteRow("Total returned", formatArgstrites(quote.totalPayout), EmeraldGlow, bold = true)
                QuoteRow("Profit", "+" + formatArgstrites(quote.finalInterest), EmeraldGlow)

                Spacer(Modifier.height(8.dp))
                Text(
                    "Estimated at today's bonuses. Bonuses are applied when you CLAIM, so a title " +
                        "bought mid-term or an active app event makes this larger.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        tapFeedback()
                        val principal = amount
                        val term = selectedTerm
                        scope.launch {
                            when (val result = bankRepo.open(principal, term)) {
                                is BankOpenResult.Success -> {
                                    confirmFeedback()
                                    messageIsError = false
                                    amountText = ""
                                    message = formatArgstrites(principal) + " Argstrites locked for " + term.label + "."
                                }
                                is BankOpenResult.BelowMinimum -> {
                                    messageIsError = true
                                    message = "The minimum deposit is " + formatArgstrites(result.minimum) + " Argstrites."
                                }
                                is BankOpenResult.NotEnoughArgstrites -> {
                                    messageIsError = true
                                    message = "You only have " + formatArgstrites(result.balance) + " Argstrites available."
                                }
                                is BankOpenResult.TooManyOpen -> {
                                    messageIsError = true
                                    message = "You already have " + result.limit + " deposits running. Claim one first."
                                }
                            }
                        }
                    },
                    enabled = canAfford && meetsMinimum && hasRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            !hasRoom -> "All " + BANK_MAX_OPEN_DEPOSITS + " slots in use"
                            amount == 0 -> "Enter an amount"
                            !meetsMinimum -> "Minimum " + formatArgstrites(BANK_MIN_DEPOSIT)
                            !canAfford -> "Short by " + formatArgstrites(amount - balance)
                            else -> "Lock " + formatArgstrites(amount) + " for " + selectedTerm.label
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        message?.let { text ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background((if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.16f))
                        .border(1.dp, (if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text,
                        color = if (messageIsError) CoralGlow else EmeraldGlow,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Your deposits", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    openDeposits.size.toString() + " / " + BANK_MAX_OPEN_DEPOSITS,
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        if (openDeposits.isEmpty()) {
            item {
                Text(
                    "Nothing locked right now. A 3-day deposit is the sweet spot if you plan to check back in.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        } else {
            items(openDeposits, key = { it.id }) { deposit ->
                DepositCard(
                    deposit = deposit,
                    nowTick = nowTick,
                    bonusPercent = bonusPercent,
                    multiplier = multiplier,
                    onClaim = {
                        tapFeedback()
                        scope.launch {
                            val result = bankRepo.close(deposit.id)
                            if (result != null) {
                                confirmFeedback()
                                messageIsError = false
                                message = "Claimed " + formatArgstrites(result.totalReturned) +
                                    " Argstrites - " + formatArgstrites(result.interestPaid) + " of it interest."
                            }
                        }
                    },
                    onBreak = { tapFeedback(); pendingEarlyExit = deposit }
                )
            }
        }

        if (closedDeposits.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, GlassBorderSoft, RoundedCornerShape(12.dp))
                        .clickable { tapFeedback(); showHistory = !showHistory }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (showHistory) "Hide history" else "Show history (" + closedDeposits.size + ")",
                        color = CyanGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
            if (showHistory) {
                items(closedDeposits, key = { it.id }) { deposit ->
                    HistoryRow(deposit)
                }
            }
        }
    }
}

@Composable
private fun QuoteRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            color = TextSecondary,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = MaterialTheme.typography.labelMedium.fontSize
        )
    }
}

@Composable
private fun SmallPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}

@Composable
private fun TermPill(term: DepositTerm, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AmberGlow.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) AmberGlow else GlassBorderSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            term.label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            maxLines = 1
        )
        Text(
            "+" + fmtRate(term.ratePercent) + "%",
            color = if (selected) AmberGlow else TextMuted,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}

@Composable
private fun DepositCard(
    deposit: Deposit,
    nowTick: Long,
    bonusPercent: Float,
    multiplier: Float,
    onClaim: () -> Unit,
    onBreak: () -> Unit
) {
    val matured = deposit.isMatured(nowTick)
    val held = deposit.millisHeld(nowTick)
    val band = BankMath.earlyExitBand(held)
    val quote = BankMath.quote(deposit.principal, deposit.term, bonusPercent, multiplier)
    val accent = if (matured) EmeraldGlow else CyanGlow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (matured) Modifier.pulsingGlow(EmeraldGlow, 16.dp)
                else Modifier.border(1.dp, GlassBorderSoft, RoundedCornerShape(16.dp))
            )
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.07f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatArgstrites(deposit.principal) + " for " + deposit.term.label,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (matured) "Matured - ready to claim"
                    else "Matures in " + fmtDuration(deposit.millisRemaining(nowTick)),
                    color = if (matured) EmeraldGlow else TextSecondary,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatArgstrites(quote.totalPayout),
                    color = EmeraldGlow,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "+" + formatArgstrites(quote.finalInterest),
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        if (!matured) {
            Spacer(Modifier.height(8.dp))
            val note = when (band) {
                EarlyExitBand.GRACE ->
                    "Free to break for another " + fmtDuration(BankMath.millisUntilPenaltyStarts(held)) + " - full principal back."
                EarlyExitBand.PENALTY ->
                    "Breaking now costs " + BANK_EARLY_PENALTY_PERCENT + "%. Penalty-free again in " +
                        fmtDuration(BankMath.millisUntilPenaltyEnds(held)) + "."
                EarlyExitBand.FREE ->
                    "Past the penalty window - breaking now returns your full principal, just no interest."
            }
            val noteColor = when (band) {
                EarlyExitBand.GRACE -> EmeraldGlow
                EarlyExitBand.PENALTY -> CoralGlow
                EarlyExitBand.FREE -> TextMuted
            }
            Text(note, color = noteColor, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        }

        Spacer(Modifier.height(10.dp))
        if (matured) {
            Button(
                onClick = onClaim,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Claim " + formatArgstrites(quote.totalPayout), fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = onBreak,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Withdraw early", fontSize = MaterialTheme.typography.labelMedium.fontSize) }
        }
    }
}

@Composable
private fun HistoryRow(deposit: Deposit) {
    val good = deposit.interestPaid > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatArgstrites(deposit.principal) + " - " + deposit.term.label,
                color = TextPrimary,
                fontSize = MaterialTheme.typography.labelMedium.fontSize
            )
            Text(
                deposit.outcome?.label ?: "Closed",
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatArgstrites(deposit.returnedAmount),
                color = if (good) EmeraldGlow else TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.labelMedium.fontSize
            )
            if (deposit.penaltyPaid > 0) {
                Text(
                    "-" + formatArgstrites(deposit.penaltyPaid) + " penalty",
                    color = CoralGlow,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            } else if (deposit.interestPaid > 0) {
                Text(
                    "+" + formatArgstrites(deposit.interestPaid) + " interest",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }
    }
}
