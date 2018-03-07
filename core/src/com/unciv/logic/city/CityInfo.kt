package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.TileResource
import com.unciv.models.linq.Counter
import com.unciv.models.stats.Stats

class CityInfo {
    @Transient
    lateinit var civInfo: CivilizationInfo
    var cityLocation: Vector2 = Vector2.Zero
    var name: String = ""

    var population = PopulationManager()
    var cityConstructions = CityConstructions()
    var expansion = CityExpansionManager()
    var cityStats = CityStats()

    internal val tileMap: TileMap
        get() = civInfo.gameInfo.tileMap

    val tile: TileInfo
        get() = tileMap[cityLocation]
    val tilesInRange: List<TileInfo>
        get() = tileMap.getTilesInDistance(cityLocation, 3).filter { civInfo.civName == it.owner }

    private val CityNames = arrayOf("New Bark", "Cherrygrove", "Violet", "Azalea", "Goldenrod", "Ecruteak", "Olivine", "Cianwood", "Mahogany", "Blackthorn", "Pallet", "Viridian", "Pewter", "Cerulean", "Vermillion", "Lavender", "Celadon", "Fuchsia", "Saffron", "Cinnibar")

    // Remove resources required by buildings
    fun getCityResources(): Counter<TileResource> {
        val cityResources = Counter<TileResource>()

        for (tileInfo in tilesInRange.filter { it.resource != null }) {
            val resource = tileInfo.tileResource
            if (resource.improvement == tileInfo.improvement || tileInfo.isCityCenter)
                cityResources.add(resource, 1)
        }

        for (building in cityConstructions.getBuiltBuildings().filter { it.requiredResource != null }) {
            val resource = GameBasics.TileResources[building.requiredResource]
            cityResources.add(resource, -1)
        }
        return cityResources
    }

    val buildingUniques: List<String?>
        get() = cityConstructions.getBuiltBuildings().filter { it.unique!=null }.map { it.unique }

    val greatPersonPoints: Stats
        get() {
            var greatPersonPoints = population.specialists.times(3f)

            for (building in cityConstructions.getBuiltBuildings())
                if (building.greatPersonPoints != null)
                    greatPersonPoints.add(building.greatPersonPoints!!)

            if (civInfo.buildingUniques.contains("GreatPersonGenerationIncrease"))
                greatPersonPoints = greatPersonPoints.times(1.33f)
            if (civInfo.policies.isAdopted("Entrepreneurship"))
                greatPersonPoints.gold *= 1.25f
            if (civInfo.policies.isAdopted("Freedom"))
                greatPersonPoints = greatPersonPoints.times(1.25f)

            return greatPersonPoints
        }

    constructor()   // for json parsing, we need to have a default constructor


    constructor(civInfo: CivilizationInfo, cityLocation: Vector2) {

        this.civInfo = civInfo
        setTransients()

        name = CityNames[civInfo.cities.size]
        this.cityLocation = cityLocation
        civInfo.cities.add(this)
        civInfo.gameInfo.addNotification(name + " has been founded!", cityLocation)
        if (civInfo.policies.isAdopted("Legalism") && civInfo.cities.size <= 4) cityConstructions.addCultureBuilding()
        if (civInfo.cities.size == 1) {
            cityConstructions.builtBuildings.add("Palace")
            cityConstructions.currentConstruction = "Worker" // Default for first city only!
        }

        for (tileInfo in civInfo.gameInfo.tileMap.getTilesInDistance(cityLocation, 1)) {
            tileInfo.owner = civInfo.civName
        }

        val tile = tile
        tile.workingCity = this.name
        tile.roadStatus = RoadStatus.Railroad
        if (listOf("Forest", "Jungle", "Marsh").contains(tile.terrainFeature))
            tile.terrainFeature = null

        population.autoAssignWorker()
        cityStats.update()
    }

    fun setTransients() {
        population.cityInfo = this
        expansion.cityInfo = this
        cityStats.cityInfo = this
        cityConstructions.cityInfo = this
    }


    fun nextTurn() {
        val stats = cityStats.currentCityStats
        if (cityConstructions.currentConstruction == CityConstructions.Settler && stats.food > 0) {
            stats.production += stats.food
            stats.food = 0f
        }

        population.nextTurn(stats.food)
        cityConstructions.nextTurn(stats)
        expansion.nextTurn(stats.culture)
    }

    internal fun rankTile(tile: TileInfo): Float {
        val stats = tile.getTileStats(this, civInfo)
        var rank = 0.0f
        if (stats.food <= 2) rank += stats.food
        else rank += (2 + (stats.food - 2) / 2f) // 1 point for each food up to 2, from there on half a point
        rank += (stats.gold / 2)
        rank += stats.production
        rank += stats.science
        rank += stats.culture
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.resource != null) rank += 1.0f
        return rank
    }
}