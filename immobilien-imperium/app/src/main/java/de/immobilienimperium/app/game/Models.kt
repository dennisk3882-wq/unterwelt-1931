package de.immobilienimperium.app.game

import java.io.Serializable

enum class PropertyType(val label: String) : Serializable {
    BRUCHBUDE("Bruchbude"),
    WOHNUNG("Eigentumswohnung"),
    REIHENHAUS("Reihenhaus"),
    EINFAMILIENHAUS("Einfamilienhaus"),
    MEHRFAMILIENHAUS("Mehrfamilienhaus"),
    PREMIUM("Premium-Immobilie"),
    LUXUS("Luxusanwesen"),
    GEWERBE("Gewerbeimmobilie")
}

enum class District(val label: String, val baseFactor: Double) : Serializable {
    PROBLEM("Problemviertel", 0.72),
    VORSTADT("Vorstadt", 1.00),
    INNENSTADT("Innenstadt", 1.24),
    NEUBAU("Neubaugebiet", 1.10),
    SEE("Seeviertel", 1.42),
    LUXUS("Luxusviertel", 1.75),
    INDUSTRIE("Industriegebiet", 0.82)
}

enum class Risk(val label: String) : Serializable { HOCH("Hoch"), MITTEL("Mittel"), NIEDRIG("Niedrig") }
enum class PropertyStatus : Serializable { MARKET, OWNED, AUCTION }
enum class TenantType(val label: String, val stability: Double, val rentFactor: Double) : Serializable {
    STUDENT("Student", .78, .92),
    FAMILIE("Familie", .92, 1.00),
    SENIOR("Senior", .96, .96),
    BUSINESS("Geschäftskunde", .88, 1.12),
    LUXUS("Luxusmieter", .86, 1.28)
}

enum class UpgradeType(val label: String, val costFactor: Double, val valueBoost: Double, val rentBoost: Double, val conditionBoost: Double) : Serializable {
    KUECHE("Küche", .035, .055, .045, .5),
    BAD("Bad", .040, .060, .050, .6),
    DACH("Dach", .065, .075, .015, 1.0),
    HEIZUNG("Heizung", .050, .060, .035, .8),
    FASSADE("Fassade", .045, .060, .020, .6),
    GARTEN("Garten", .025, .035, .025, .2),
    SMART_HOME("Smart Home", .030, .045, .035, .1),
    POOL("Pool", .070, .085, .055, .1)
}

enum class GameMode(val label: String) : Serializable { CAREER("Karriere"), SCENARIO("Szenario"), ENDLESS("Endlos") }

data class Property(
    val id: Int,
    var name: String,
    var address: String,
    var type: PropertyType,
    var district: District,
    var purchasePrice: Double,
    var currentValue: Double,
    var monthlyRent: Double,
    var renovationCost: Double,
    var annualMaintenance: Double,
    var locationRating: Double,
    var condition: Double,
    var annualAppreciation: Double,
    var risk: Risk,
    var prestige: Int,
    var status: PropertyStatus = PropertyStatus.MARKET,
    var occupied: Boolean = false,
    var tenant: TenantType? = null,
    var outstandingLoan: Double = 0.0,
    var monthlyLoanPayment: Double = 0.0,
    var interestRate: Double = 0.0,
    var originalLoanTermMonths: Int = 240,
    var loanMonthsRemaining: Int = 0,
    var upgrades: MutableSet<UpgradeType> = mutableSetOf(),
    var monthsOwned: Int = 0
) : Serializable {
    fun equity(): Double = (currentValue - outstandingLoan).coerceAtLeast(0.0)
    fun projectedSalePrice(): Double {
        val conditionFactor = 0.90 + condition.coerceIn(0.0, 10.0) * 0.018
        return currentValue * conditionFactor
    }
}

data class Competitor(
    val name: String,
    val strategy: String,
    var cashPower: Double,
    var propertiesOwned: Int = 0,
    var prestige: Int = 0
) : Serializable

data class MarketEvent(
    val title: String,
    val description: String,
    val district: District? = null,
    val valueMultiplier: Double = 1.0,
    val rentMultiplier: Double = 1.0,
    val durationMonths: Int = 1
) : Serializable

data class ActiveEffect(
    val title: String,
    val district: District?,
    val valueMultiplier: Double,
    val rentMultiplier: Double,
    var monthsRemaining: Int
) : Serializable

data class MonthlySnapshot(
    val year: Int,
    val month: Int,
    val netWorth: Double,
    val cash: Double,
    val propertyValue: Double,
    val cashflow: Double
) : Serializable

data class AuctionState(
    var propertyId: Int? = null,
    var currentBid: Double = 0.0,
    var playerLeading: Boolean = false,
    var monthsRemaining: Int = 0
) : Serializable

data class GameState(
    var year: Int = 1,
    var month: Int = 1,
    var cash: Double = 100_000.0,
    var prestige: Int = 0,
    var creditScore: Int = 650,
    var careerLevel: Int = 1,
    var mode: GameMode = GameMode.CAREER,
    var market: MutableList<Property> = mutableListOf(),
    var owned: MutableList<Property> = mutableListOf(),
    var competitors: MutableList<Competitor> = mutableListOf(),
    var activeEffects: MutableList<ActiveEffect> = mutableListOf(),
    var eventLog: MutableList<String> = mutableListOf(),
    var history: MutableList<MonthlySnapshot> = mutableListOf(),
    var auction: AuctionState = AuctionState(),
    var lastCashflow: Double = 0.0,
    var gameWon: Boolean = false,
    var scenarioTargetReached: Boolean = false,
    var nextPropertyId: Int = 1000
) : Serializable {
    fun propertyValue(): Double = owned.sumOf { it.currentValue }
    fun debt(): Double = owned.sumOf { it.outstandingLoan }
    fun netWorth(): Double = cash + propertyValue() - debt()
    fun monthlyRentGross(): Double = owned.filter { it.occupied }.sumOf { it.monthlyRent * (it.tenant?.rentFactor ?: 1.0) }
    fun monthlyLoanPayments(): Double = owned.sumOf { it.monthlyLoanPayment }
    fun occupancyRate(): Double = if (owned.isEmpty()) 0.0 else owned.count { it.occupied }.toDouble() / owned.size
}
