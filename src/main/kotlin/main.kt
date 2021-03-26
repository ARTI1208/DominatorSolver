import java.util.regex.Pattern

data class Dominator(val block: BaseBlock, private val dominatorList: Set<Dominator>) {

    val dominators: Set<Dominator>

    val idom: Dominator? get() = dominators.lastOrNull { it != this }

    init {

        dominators = (setOf(this) + dominatorList).sortedWith { o1, o2 ->
            o1.block.compareTo(o2.block)
        }.toSet()

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Dominator) return false

        if (block != other.block) return false

        return true
    }

    override fun hashCode(): Int {
        return block.hashCode()
    }

    override fun toString(): String {
        return "Dominator(block=${block.name}, idom=${idom?.block?.name}, dominators=${dominators.joinToString(prefix = "[", postfix = "]") { it.block.name }})"
    }


}

data class BaseBlock(val name: String) : Comparable<BaseBlock> {

    val posts = mutableListOf<BaseBlock>()
    val preds = mutableListOf<BaseBlock>()

    operator fun plusAssign(baseBlock: BaseBlock) {
        posts += baseBlock
        baseBlock.preds += this
    }

    operator fun plusAssign(baseBlocks: List<BaseBlock>) {
        posts += baseBlocks
        baseBlocks.forEach {
            it.preds += this
        }
    }

    private fun createDominator(processedBlocks: MutableMap<BaseBlock, Dominator?>, predDoms: Set<Dominator>): Dominator {

        val dominator = Dominator(this, predDoms)

        val prev = processedBlocks.put(this, dominator)

//        if (dominator != prev) {
//            println("NewDom: $dominator")
//        }

        return dominator
    }

    fun log(any: Any) {
        val testName = "SKIP"
//        if (name == testName) {
        if (false) {
            println(any)
        }
    }

    fun calculateDominator(processedBlocks: MutableMap<BaseBlock, Dominator?>, forceReCalc: Boolean = false): Dominator {

        log("CalcDom: $name")

        if (this !in processedBlocks) {
            processedBlocks += this to null
        } else {
            val dom = processedBlocks[this]
            if (dom != null && !forceReCalc) return dom
        }

        if (preds.isEmpty()) {
            println("$name = {$name}")
            return createDominator(processedBlocks, emptySet())
        }

        val fst = preds.first()
        if (fst !in processedBlocks) {
            println("u_$name = {$name}")
            return createDominator(processedBlocks, emptySet())
        }

        val map = HashMap<String, Set<String>>()

        val fstDom = fst.calculateDominator(processedBlocks).dominators

        map += fst.name to fstDom.mapTo(HashSet()) { it.block.name }

        log("Pred: ${fst.name}; ${fstDom.map { it.block.name }.joinToString()}")

        val predDoms = preds.drop(1).fold(fstDom) { acc, baseBlock ->
            log("Pred: ${baseBlock.name}")
            if (baseBlock !in processedBlocks) {

                map += baseBlock.name to mutableSetOf("N1")

                log("Not processed yet: ${baseBlock.name}")
                return@fold acc
            }
            if (baseBlock in processedBlocks && processedBlocks[baseBlock] == null) {

                map += baseBlock.name to mutableSetOf("N2")

                log("Processing is in progress: ${baseBlock.name}")
                return@fold acc
            }
//            if (baseBlock == this) {
//                log("Link to itself: ${baseBlock.name}")
//                return@fold acc
//            }

            val nextDom = baseBlock.calculateDominator(processedBlocks).dominators

            map += baseBlock.name to nextDom.mapTo(HashSet()) { it.block.name }

            log(nextDom.map { it.block }.joinToString())

            acc.intersect(nextDom)
        }

        println("$name = {$name} \\/ (${map.keys.joinToString(separator = " /\\ ") { "D($it)" }}) = {$name} \\/ (${
            map.entries.joinToString(
                separator = " /\\ "
            ) { it.value.joinToString(
                prefix = "{",
                postfix = "}"
            ) { 
                it
            } }
        }) = ${(predDoms + Dominator(this, emptySet())).joinToString(
            prefix = "{",
            postfix = "}") { it.block.name }}")

        return createDominator(processedBlocks, predDoms)
    }

    fun calculateDominator(): Dominator {
        return calculateDominator(mutableMapOf())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseBlock) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "BaseBlock(name='$name', posts=${posts.joinToString(prefix = "[", postfix = "]") { it.name }}, preds=${preds.joinToString(prefix = "[", postfix = "]") { it.name }})"
    }

    override fun compareTo(other: BaseBlock): Int {

        val pattern = Pattern.compile("\\d+")

        val matcher = pattern.matcher(name)
        val matcher2 = pattern.matcher(other.name)

        return if (matcher.find()) {
            matcher2.find()
            val o1Num = matcher.group(0).toInt()
            val o2Num = matcher2.group(0).toInt()

            o1Num - o2Num
        } else {
            name.compareTo(other.name)
        }
    }


}


//class DominatorTreeNode(val dominator: Dominator, val parentNode: DominatorTreeNode? = null)

class CFG(val blocks: List<BaseBlock>) {


    fun calculateDominators(): List<Dominator> {
        val map = mutableMapOf<BaseBlock, Dominator?>()



        var dominators = blocks.map { it.calculateDominator(map) }
        println("===========================")
        var nextIterDoms = blocks.map { it.calculateDominator(map, true) }

        while (dominators != nextIterDoms) {
            dominators = nextIterDoms
            println("===========================")
            nextIterDoms = blocks.map { it.calculateDominator(map, true) }
        }

        return dominators
    }

    fun calculateBoundaries(dominators : List<Dominator>): Map<BaseBlock, Set<BaseBlock>> {

        val dfs = mutableMapOf<BaseBlock, MutableSet<BaseBlock>>()

        blocks.forEach {
            dfs[it] = mutableSetOf()
        }

        blocks.forEachIndexed { index, baseBlock ->

            if (baseBlock.preds.size > 1) {
                val blockIDominator = dominators[index].idom?.block
                println("Pred(${baseBlock.name}) = ${baseBlock.preds.joinToString(prefix = "{", postfix = "}") { it.name }}, IDom(${baseBlock.name}) = {${blockIDominator?.name ?: "-"}}")
                baseBlock.preds.forEach {
                    var curPred: BaseBlock? = it
                    while (curPred != null && curPred != blockIDominator) {

                        val curPredDom = dominators.first { it.block == curPred }.idom?.block

                        println("${curPred.name} -> ${curPredDom?.name ?: "-"}: DF(${curPred.name}) += {${baseBlock.name}}")

                        dfs[curPred]?.plusAssign(baseBlock)

                        curPred = curPredDom
                    }
                }
            }

        }

        return dfs.toSortedMap()
    }

}

fun main(args: Array<String>) {

//    testDomBounds()


    val blockA = BaseBlock("A")
    val blockB = BaseBlock("B")
    val blockC = BaseBlock("C")
    val blockD = BaseBlock("D")
    val blockE = BaseBlock("E")

    blockA += listOf(blockB, blockE)
    blockB += listOf(blockC)
    blockC += listOf(blockC, blockD)
    blockD += listOf(blockB, blockE)

    val blocks = listOf(blockA, blockB, blockC, blockD, blockE)

    val cfg = CFG(blocks)

    val dominators = cfg.calculateDominators()
    println("IDoms: " + dominators.joinToString { "(${it.block.name}, ${it.idom?.block?.name ?: "-"})" })
    println()


    val boundaries = cfg.calculateBoundaries(dominators)

    println("Boundaries: " + boundaries.entries.joinToString { "(${it.key.name}: " + it.value.joinToString(prefix = "{", postfix = "}") { it.name } + ")" })
}

fun testDomTree() {

    val blockB1 = BaseBlock("B1")
    val blockB2 = BaseBlock("B2")
    val blockB3 = BaseBlock("B3")
    val blockB4 = BaseBlock("B4")
    val blockB5 = BaseBlock("B5")
    val blockB6 = BaseBlock("B6")
    val blockB7 = BaseBlock("B7")
    val blockB8 = BaseBlock("B8")
    val blockB9 = BaseBlock("B9")
    val blockB10 = BaseBlock("B10")

    blockB1 += listOf(blockB2, blockB3)
    blockB2 += listOf(blockB3)
    blockB3 += listOf(blockB4)
    blockB4 += listOf(blockB3, blockB5, blockB6)
    blockB5 += listOf(blockB7)
    blockB6 += listOf(blockB7)
    blockB7 += listOf(blockB4, blockB8)
    blockB8 += listOf(blockB3, blockB9, blockB10)
    blockB9 += listOf(blockB1)
    blockB10 += listOf(blockB7)

    val blocks = listOf(
        blockB1, blockB2, blockB3, blockB4, blockB5,
        blockB6, blockB7, blockB8, blockB9, blockB10
    )

    val cfg = CFG(blocks)

    val dominators = cfg.calculateDominators()
    println(dominators.joinToString { "(${it.block.name}, ${it.idom?.block?.name ?: "-"})" })

    val boundaries = cfg.calculateBoundaries(dominators)
    println(boundaries.entries.joinToString { "(${it.key.name}: " + it.value.joinToString(prefix = "{", postfix = "}") { it.name } + ")" })
}

fun testDomBounds() {
    val blockB0 = BaseBlock("B0")
    val blockB1 = BaseBlock("B1")
    val blockB2 = BaseBlock("B2")
    val blockB3 = BaseBlock("B3")
    val blockB4 = BaseBlock("B4")
    val blockB5 = BaseBlock("B5")
    val blockB6 = BaseBlock("B6")
    val blockB7 = BaseBlock("B7")


    blockB0 += listOf(blockB1)
    blockB1 += listOf(blockB2, blockB3)
    blockB2 += listOf(blockB7)
    blockB3 += listOf(blockB4, blockB5)
    blockB4 += listOf(blockB6)
    blockB5 += listOf(blockB6)
    blockB6 += listOf(blockB7)
    blockB7 += listOf(blockB1)

    val blocks = listOf(
        blockB0, blockB1, blockB2, blockB3, blockB4,
        blockB5, blockB6, blockB7
    )

    val cfg = CFG(blocks)


    val dominators = cfg.calculateDominators()
    println(dominators.joinToString { "(${it.block.name}, ${it.idom?.block?.name ?: "-"})" })

    val boundaries = cfg.calculateBoundaries(dominators)
    println(boundaries.entries.joinToString { "(${it.key.name}: " + it.value.joinToString(prefix = "{", postfix = "}") { it.name } + ")" })
}

fun testDomBounds2() {
    val blockA = BaseBlock("a")
    val blockB = BaseBlock("b")
    val blockC = BaseBlock("c")
    val blockD = BaseBlock("d")
    val blockE = BaseBlock("e")
    val blockF = BaseBlock("f")
    val blockG = BaseBlock("g")
    val blockH = BaseBlock("h")
    val blockK = BaseBlock("k")


    blockA += listOf(blockB, blockC)
    blockB += listOf(blockK)
    blockC += listOf(blockD, blockE)
    blockD += listOf(blockF)
    blockE += listOf(blockF)
    blockF += listOf(blockG, blockH)
    blockG += listOf(blockH)
    blockH += listOf(blockK)

    val blocks = listOf(
        blockA, blockB, blockC, blockD, blockE,
        blockF, blockG, blockH, blockK
    )

    val cfg = CFG(blocks)


    val dominators = cfg.calculateDominators()
    println(dominators.joinToString { "(${it.block.name}, ${it.idom?.block?.name ?: "-"})" })

    val boundaries = cfg.calculateBoundaries(dominators)
    println(boundaries.entries.joinToString { "(${it.key.name}: " + it.value.joinToString(prefix = "{", postfix = "}") { it.name } + ")" })
}