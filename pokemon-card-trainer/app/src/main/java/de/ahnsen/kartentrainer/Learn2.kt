package de.ahnsen.kartentrainer

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class LearnLesson(
    val title: String,
    val text: String
)

private data class GuidedStep(
    val title: String,
    val instruction: String,
    val explanation: String,
    val hand: Int,
    val deck: Int,
    val prizes: Int,
    val active: String,
    val bench: Int,
    val energy: Int
)

@Composable
fun AdvancedLearnScreen(padding: PaddingValues) {
    var mode by rememberSaveable { mutableStateOf("Kurs") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == "Kurs",
                onClick = { mode = "Kurs" },
                label = { Text("Regelkurs") }
            )
            FilterChip(
                selected = mode == "Spiel",
                onClick = { mode = "Spiel" },
                label = { Text("Geführtes Erstspiel") }
            )
        }

        if (mode == "Kurs") {
            RuleCourse(Modifier.weight(1f))
        } else {
            GuidedFirstGame(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RuleCourse(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lessons = remember {
        listOf(
            LearnLesson(
                "1 · Ziel der Partie",
                "Jeder Spieler hat ein eigenes Deck mit exakt 60 Karten. Du gewinnst normalerweise, indem du alle 6 eigenen Preiskarten nimmst. Du kannst auch gewinnen, wenn dein Gegner kein Pokémon mehr im Spiel hat oder zu Beginn seines Zuges keine Karte ziehen kann."
            ),
            LearnLesson(
                "2 · Sammlung und Deck",
                "Deine Sammlung darf beliebig groß sein und Karten aus vielen Sets enthalten. Für eine Partie wählst du daraus genau 60 Karten. Außer Basis-Energien dürfen Karten mit demselben Namen grundsätzlich höchstens viermal im Deck vorkommen."
            ),
            LearnLesson(
                "3 · Spielstart und Mulligan",
                "Jeder zieht 7 Karten. Du brauchst mindestens ein Basis-Pokémon. Hast du keines, zeigst du die Hand, mischst sie zurück und ziehst erneut. Das ist ein Mulligan; der Gegner darf dafür zusätzliche Karten ziehen."
            ),
            LearnLesson(
                "4 · Aktives Pokémon und Bank",
                "Ein Basis-Pokémon wird aktiv. Bis zu fünf weitere Basis-Pokémon können auf die Bank. Wird das aktive Pokémon kampfunfähig, muss ein Bank-Pokémon nach vorn."
            ),
            LearnLesson(
                "5 · Startspieler",
                "Der Startspieler zieht zu Beginn seines ersten Zuges eine Karte, darf in diesem ersten Zug aber keinen Unterstützer spielen und nicht angreifen. Der zweite Spieler darf in seinem ersten Zug angreifen, wenn die Energiekosten erfüllt sind."
            ),
            LearnLesson(
                "6 · Zugaufbau",
                "Ziehe eine Karte. Danach darfst du Basis-Pokémon auf die Bank legen, entwickeln, normalerweise genau eine Energie aus der Hand anlegen, Trainerkarten spielen, Fähigkeiten nutzen und einmal zurückziehen. Ein Angriff beendet den Zug."
            ),
            LearnLesson(
                "7 · Energiefarben",
                "Nicht nur die Anzahl zählt: farbige Energiesymbole müssen mit passenden Energietypen bezahlt werden. Farblos kann mit beliebigen Energien bezahlt werden. Spezialenergien können zusätzliche Regeln oder mehrere Energieeinheiten liefern."
            ),
            LearnLesson(
                "8 · Entwicklung",
                "Ein Pokémon kann normalerweise nicht in demselben Zug entwickelt werden, in dem es ins Spiel kam. Entwickle die passende Stufe auf die Vorentwicklung. Schadensmarken und angelegte Energie bleiben erhalten."
            ),
            LearnLesson(
                "9 · Trainerkarten",
                "Items können meist beliebig oft gespielt werden. Unterstützer grundsätzlich einmal pro Zug. Stadien bleiben im Spiel, bis sie ersetzt werden. Pokémon-Ausrüstungen bleiben am Pokémon, bis ein Effekt sie entfernt oder das Pokémon das Spiel verlässt."
            ),
            LearnLesson(
                "10 · Schaden und K. o.",
                "Nach dem Grundschaden verändern Kartentext, Schwäche, Resistenz und dauerhafte Effekte das Ergebnis. Sind mindestens so viele Schadenspunkte vorhanden wie das Pokémon maximale KP hat, ist es kampfunfähig."
            ),
            LearnLesson(
                "11 · Sonderzustände",
                "Vergiftung, Verbrennung, Schlaf, Paralyse und Verwirrung verändern das Spiel. Manche werden zwischen den Zügen geprüft; Entwicklung und Rückkehr auf die Bank können Zustände entfernen."
            ),
            LearnLesson(
                "12 · Rückzug",
                "Zum Rückzug musst du die auf der Karte angegebenen Energiekosten ablegen. Du darfst normalerweise nur einmal pro Zug zurückziehen. Schlafende oder paralysierte Pokémon können nicht normal zurückziehen."
            ),
            LearnLesson(
                "13 · Fähigkeiten und Effekte",
                "Fähigkeiten können aktiv, passiv, beim Ausspielen oder als Reaktion ausgelöst werden. Der genaue Kartentext hat Vorrang vor allgemeinen Regeln. Die App zeigt bei komplizierten Texten, was sie automatisch ausführt und was manuell geprüft werden muss."
            ),
            LearnLesson(
                "14 · Formate",
                "Verschiedene Sets dürfen gemischt werden. Für Standard oder Erweitert zählt die Zulässigkeit der einzelnen Karte. Für eine freie Partie könnt ihr gemeinsam andere Karten erlauben."
            ),
            LearnLesson(
                "15 · Sammeln und Wert",
                "Seltenheit und Marktwert sagen nichts darüber aus, ob eine Karte spielstark ist. Zustand, Sprache, Variante und reale Nachfrage beeinflussen den Preis; die App zeigt deshalb nur eine Schätzung."
            )
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val lesson = lessons[index]
    val lessonColors = listOf(
        KidPalette.SoftPurple,
        KidPalette.SoftBlue,
        KidPalette.SoftGreen,
        KidPalette.SoftYellow,
        KidPalette.SoftPink
    )
    val lessonColor = lessonColors[index % lessonColors.size]

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(Unit) {
        if (ttsReady) tts.language = Locale.GERMAN
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / lessons.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = lessonColor
            )
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = KidPalette.Purple
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = KidPalette.Ink
                )
                Spacer(Modifier.height(12.dp))
                Text(lesson.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    enabled = ttsReady,
                    onClick = {
                        tts.language = Locale.GERMAN
                        tts.speak(
                            lesson.title + ". " + lesson.text,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "lesson-" + index
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    Text(" Vorlesen")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Zurück") }
            Button(
                onClick = {
                    if (index < lessons.lastIndex) index++ else index = 0
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (index < lessons.lastIndex) "Weiter" else "Nochmal") }
        }
    }
}

@Composable
private fun GuidedFirstGame(modifier: Modifier = Modifier) {
    val steps = remember {
        listOf(
            GuidedStep(
                "Deck vorbereiten",
                "Stell dir vor, dein 60-Karten-Deck ist gemischt. Tippe auf „Aktion ausführen“.",
                "Vor jeder Partie werden beide Decks gründlich gemischt.",
                0, 60, 6, "–", 0, 0
            ),
            GuidedStep(
                "Starthand ziehen",
                "Ziehe 7 Karten.",
                "Wenn kein Basis-Pokémon dabei wäre, müsstest du einen Mulligan nehmen.",
                7, 53, 6, "–", 0, 0
            ),
            GuidedStep(
                "Basis-Pokémon aktiv",
                "Lege ein Basis-Pokémon verdeckt als aktives Pokémon.",
                "Dein aktives Pokémon kämpft vorne. Die Bank steht dahinter.",
                6, 53, 6, "Übungs-Pokémon", 0, 0
            ),
            GuidedStep(
                "Bank aufbauen",
                "Lege zwei weitere Basis-Pokémon auf die Bank.",
                "Du darfst bis zu fünf Pokémon auf deiner Bank haben.",
                4, 53, 6, "Übungs-Pokémon", 2, 0
            ),
            GuidedStep(
                "Preiskarten",
                "Lege 6 Karten verdeckt als Preiskarten zur Seite.",
                "Diese Karten werden erst genommen, wenn du gegnerische Pokémon kampfunfähig machst.",
                4, 47, 6, "Übungs-Pokémon", 2, 0
            ),
            GuidedStep(
                "Erste Karte ziehen",
                "Ziehe zu Beginn deines Zuges eine Karte.",
                "Fast jeder Zug beginnt mit genau einer gezogenen Karte.",
                5, 46, 6, "Übungs-Pokémon", 2, 0
            ),
            GuidedStep(
                "Energie anlegen",
                "Lege eine passende Basis-Energie an dein aktives Pokémon.",
                "Normalerweise darfst du nur eine Energie aus deiner Hand pro Zug anlegen.",
                4, 46, 6, "Übungs-Pokémon", 2, 1
            ),
            GuidedStep(
                "Trainerkarte",
                "Spiele ein Item und ziehe zwei Übungskarten.",
                "Items helfen beim Ziehen und Suchen. Unterstützer sind stärker begrenzt.",
                5, 44, 6, "Übungs-Pokémon", 2, 1
            ),
            GuidedStep(
                "Angriff prüfen",
                "Prüfe die Energiekosten deiner Attacke.",
                "Sind alle farbigen und farblosen Kosten bezahlt, darfst du die Attacke wählen.",
                5, 44, 6, "Übungs-Pokémon", 2, 1
            ),
            GuidedStep(
                "Angreifen",
                "Führe die Attacke aus.",
                "Nach dem Angriff endet dein Zug. Schaden bleibt auf dem gegnerischen Pokémon liegen.",
                5, 44, 6, "Übungs-Pokémon", 2, 1
            ),
            GuidedStep(
                "Entwicklung im nächsten Zug",
                "Entwickle jetzt eines deiner Pokémon.",
                "Es lag bereits seit einem früheren Zug im Spiel und darf deshalb entwickelt werden.",
                4, 43, 6, "Übungs-Pokémon", 2, 1
            ),
            GuidedStep(
                "Rückzug verstehen",
                "Stell dir vor, dein aktives Pokémon ist stark beschädigt. Ziehe es zurück.",
                "Lege die Rückzugskosten als Energie ab und tausche mit einem Bank-Pokémon.",
                4, 43, 6, "Bank-Pokémon", 2, 0
            ),
            GuidedStep(
                "K. o. erzielen",
                "Dein neuer Angreifer macht das gegnerische Pokémon kampfunfähig.",
                "Du darfst jetzt normalerweise eine deiner Preiskarten nehmen.",
                5, 43, 5, "Bank-Pokémon", 2, 1
            ),
            GuidedStep(
                "Partie fortsetzen",
                "Wiederhole Ziehen, Aufbau und Angriff, bis eine Siegbedingung erfüllt ist.",
                "Jetzt kennst du den normalen Ablauf. Im KI-Trainer kannst du ihn mit deinen echten Karten üben.",
                5, 43, 5, "Bank-Pokémon", 2, 1
            )
        )
    }

    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LinearProgressIndicator(
                progress = { (index + 1).toFloat() / steps.size },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = KidPalette.SoftGreen
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Schritt " + (index + 1) + "/" + steps.size + " · " + step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Übungstisch", fontWeight = FontWeight.Bold)
                    Text("Hand: " + step.hand + " · Deck: " + step.deck + " · Preise: " + step.prizes)
                    Text("Aktiv: " + step.active + " · Bank: " + step.bench + " · Energie: " + step.energy)
                }
            }
        }
        item {
            OutlinedCard {
                Text(step.explanation, modifier = Modifier.padding(12.dp))
            }
        }
        item {
            Button(
                onClick = {
                    if (index < steps.lastIndex) index++ else index = 0
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(if (index < steps.lastIndex) " Aktion ausführen" else " Tutorial erneut starten")
            }
        }
        if (index > 0) {
            item {
                OutlinedButton(
                    onClick = { index-- },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vorheriger Schritt")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { index = 0 },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text(" Tutorial zurücksetzen")
            }
        }
    }
}
