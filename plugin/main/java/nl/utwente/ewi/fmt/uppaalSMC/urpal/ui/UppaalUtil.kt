package nl.utwente.ewi.fmt.uppaalSMC.urpal.ui

import java.awt.Container
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import java.util.ArrayList

import org.eclipse.emf.ecore.util.EcoreUtil
import org.muml.uppaal.expressions.CompareExpression
import org.muml.uppaal.expressions.CompareOperator
import org.muml.uppaal.expressions.Expression
import org.muml.uppaal.expressions.ExpressionsFactory
import org.muml.uppaal.expressions.IdentifierExpression
import org.muml.uppaal.expressions.IncrementDecrementOperator
import org.muml.uppaal.expressions.LiteralExpression
import org.muml.uppaal.expressions.LogicalExpression
import org.muml.uppaal.expressions.LogicalOperator
import org.muml.uppaal.templates.Location
import org.muml.uppaal.templates.Synchronization
import org.muml.uppaal.templates.SynchronizationKind
import org.muml.uppaal.templates.Template
import org.muml.uppaal.templates.TemplatesFactory
import org.muml.uppaal.types.TypesFactory

import com.uppaal.engine.Engine
import com.uppaal.engine.EngineException
import com.uppaal.engine.EngineStub
import com.uppaal.engine.Problem
import com.uppaal.gui.SystemInspector
import com.uppaal.model.core2.AbstractTemplate
import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Edge
import com.uppaal.model.core2.Node
import com.uppaal.model.system.SystemEdgeSelect
import com.uppaal.model.system.SystemLocation
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.symbolic.SymbolicState
import com.uppaal.model.system.symbolic.SymbolicTrace
import com.uppaal.model.system.symbolic.SymbolicTransition
import com.uppaal.plugin.Repository

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EObjectContainmentEList
import org.eclipse.xtext.nodemodel.INode
import org.eclipse.xtext.nodemodel.impl.RootNode
import org.eclipse.xtext.nodemodel.util.NodeModelUtils
import org.eclipse.xtext.util.LineAndColumn
import org.muml.uppaal.declarations.*
import java.lang.Exception
import kotlin.reflect.KProperty1

object UppaalUtil {
    var engine: Engine = connectToEngine()

    fun Document.getTemplatesSequence() = generateSequence(templates) {
        it.next as? AbstractTemplate
    }

    fun AbstractTemplate.getLocations(): Sequence<com.uppaal.model.core2.Location> {
        var current = first
        return generateSequence {
            while (current != null) {
                if (current as? com.uppaal.model.core2.Location != null) {
                    val result = current
                    current = current?.next
                    return@generateSequence result as com.uppaal.model.core2.Location
                } else {
                    current = current?.next
                }
            }
            null
        }
    }

    fun getSystemInspector(c: Container): SystemInspector? {
        var parent: Container? = c.parent
        while (parent != null) {
            if (parent is SystemInspector) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    fun getLocations(doc: Document, templateName: String): List<com.uppaal.model.core2.Location> {
        val t = doc.getTemplate(templateName)
        var n: Node? = t.first
        val result = ArrayList<com.uppaal.model.core2.Location>()
        while (n != null) {
            if (n is com.uppaal.model.core2.Location) {
                result.add(n)
            }
            n = n.next
        }
        return result
    }

    fun getEdges(doc: Document, templateName: String): List<Edge> {
        val t = doc.getTemplate(templateName)
        var n: Node? = t.first
        val result = ArrayList<Edge>()
        while (n != null) {
            if (n is Edge) {
                result.add(n)
            }
            n = n.next
        }
        return result
    }

    fun createVariable(name: String): Variable {
        val result = DeclarationsFactory.eINSTANCE.createVariable()
        result.name = name
        return result
    }

    @Throws(EngineException::class, IOException::class, URISyntaxException::class)
    private fun connectToEngine(): Engine {

        val os = System.getProperty("os.name")
        var path = File(Repository::class.java.protectionDomain.codeSource.location.toURI())
                .parentFile.path
        path += if ("Linux" == os) {
            "/bin-Linux/server"
        } else {
            "\\bin-Windows\\server.exe"
        }
        val engine = Engine()
        engine.setServerPath(path)
        engine.setServerHost("localhost")
        engine.setConnectionMode(EngineStub.BOTH)
        engine.connect()
        return engine
    }

    fun negate(ex: Expression): Expression? {
        if (ex is CompareExpression) {
            // omits UPPAAL bug with negated comparisons
            val comp = EcoreUtil.copy(ex)
            val id = comp.firstExpr as? IdentifierExpression ?: comp.secondExpr as? IdentifierExpression
            if (id != null && id.isClockRate) {
                return null
            }
            when (comp.operator!!) {
                CompareOperator.GREATER -> comp.operator = CompareOperator.LESS_OR_EQUAL
                CompareOperator.GREATER_OR_EQUAL -> comp.operator = CompareOperator.LESS
                CompareOperator.EQUAL -> comp.operator = CompareOperator.UNEQUAL
                CompareOperator.UNEQUAL -> comp.operator = CompareOperator.EQUAL
                CompareOperator.LESS_OR_EQUAL -> comp.operator = CompareOperator.GREATER
                CompareOperator.LESS -> comp.operator = CompareOperator.GREATER_OR_EQUAL
            }
            return comp
        }
        val result = ExpressionsFactory.eINSTANCE.createNegationExpression()
        result.negatedExpression = EcoreUtil.copy(ex)
        return result
    }

    @Throws(EngineException::class, IOException::class)
    fun compile(doc: Document): UppaalSystem {
        reconnect()
        val problems = ArrayList<Problem>()
        val sys = engine.getSystem(doc, problems)
        if (!problems.isEmpty()) {
            var fatal = false
            println("There are problems with the document:")
            for (p in problems) {
                println(p.toString())
                if ("warning" != p.type) { // ignore warnings
                    fatal = true
                }
            }
            if (fatal) {
                System.exit(1)
            }
        }
        return sys
    }

    @Throws(EngineException::class, IOException::class)
    fun reconnect() {
        engine.disconnect()
        engine.connect()
    }

    fun createLocation(t: Template, n: String): Location {
        val location = TemplatesFactory.eINSTANCE.createLocation()
        location.name = n
        t.location.add(location)
        return location
    }

    fun createTemplate(nsta: NSTA, name: String): Template {
        val template = TemplatesFactory.eINSTANCE.createTemplate()
        template.name = name
        nsta.template.add(template)
        return template
    }

    fun createEdge(source: Location, target: Location): org.muml.uppaal.templates.Edge {
        if (source.parentTemplate !== target.parentTemplate) {
            throw IllegalArgumentException("Locations are not in same template")
        }
        val edge = TemplatesFactory.eINSTANCE.createEdge()
        edge.source = source
        edge.target = target
        source.parentTemplate.edge.add(edge)
        return edge
    }

    fun addSynchronization(edge: org.muml.uppaal.templates.Edge, channel: Variable, kind: SynchronizationKind): Synchronization {
        val sync = TemplatesFactory.eINSTANCE.createSynchronization()
        sync.kind = kind
        sync.channelExpression = createIdentifier(channel)
        edge.synchronization = sync
        return sync
    }

    fun addCounterVariable(nsta: NSTA): Variable? {
        if (AbstractProperty.STATE_SPACE_SIZE <= 0) return null
        val counter = DeclarationsFactory.eINSTANCE.createDataVariableDeclaration()
        val counterVar = UppaalUtil.createVariable("_counter")
        val range = TypesFactory.eINSTANCE.createRangeTypeSpecification()
        val bounds = TypesFactory.eINSTANCE.createIntegerBounds()
        bounds.lowerBound = createLiteral("0")
        bounds.upperBound = createLiteral("" + AbstractProperty.STATE_SPACE_SIZE * 10)
        range.bounds = bounds
        counter.typeDefinition = range
        counter.prefix = DataVariablePrefix.META
        counter.variable.add(counterVar)
        nsta.globalDeclarations.declaration.add(counter)
        return counterVar
    }

    fun addCounterToEdge(edge: org.muml.uppaal.templates.Edge, counterVar: Variable?) {
        if (AbstractProperty.STATE_SPACE_SIZE <= 0) return
        if (counterVar == null) return
        if (edge.synchronization != null && edge.synchronization.kind == SynchronizationKind.RECEIVE) return
        val incr = ExpressionsFactory.eINSTANCE.createPostIncrementDecrementExpression()
        incr.expression = UppaalUtil.createIdentifier(counterVar)
        incr.operator = IncrementDecrementOperator.INCREMENT
        edge.update.add(incr)
        val compare = ExpressionsFactory.eINSTANCE.createCompareExpression()
        compare.firstExpr = UppaalUtil.createIdentifier(counterVar)
        compare.secondExpr = UppaalUtil.createLiteral("" + AbstractProperty.STATE_SPACE_SIZE)
        compare.operator = CompareOperator.LESS
        if (edge.guard == null) {
            edge.guard = compare
        } else {
            val and = ExpressionsFactory.eINSTANCE.createLogicalExpression()
            and.firstExpr = compare
            and.secondExpr = edge.guard
            and.operator = LogicalOperator.AND
            edge.guard = and
        }
    }

    fun createIdentifier(`var`: Variable): IdentifierExpression {
        val result = ExpressionsFactory.eINSTANCE.createIdentifierExpression()
        result.identifier = `var`
        return result
    }

    fun createLiteral(literal: String): LiteralExpression {
        val result = ExpressionsFactory.eINSTANCE.createLiteralExpression()
        result.text = literal
        return result
    }

    fun createChannelDeclaration(nsta: NSTA, string: String): ChannelVariableDeclaration {
        val cvd = DeclarationsFactory.eINSTANCE.createChannelVariableDeclaration()
        cvd.variable.add(UppaalUtil.createVariable(string))
        val tr = TypesFactory.eINSTANCE.createTypeReference()
        tr.referredType = nsta.chan
        cvd.typeDefinition = tr
        return cvd
    }

    fun invariantToGuard(invariant: Expression): Expression {
        var current: Expression? = invariant
        while (current != null) {
            val constr: Expression
            if (current is LogicalExpression) {
                val logic = current
                if (logic.operator == LogicalOperator.AND) {
                    constr = logic.secondExpr
                    current = logic.firstExpr
                } else {
                    break
                }
            } else {
                constr = current
                current = null
            }
            if (constr is CompareExpression) {
                if (listOf(constr.firstExpr, constr.secondExpr).filterIsInstance<IdentifierExpression>().any { it.isClockRate }) {
                    constr.firstExpr = createLiteral("0")
                    constr.secondExpr = createLiteral("0")
                    constr.operator = CompareOperator.EQUAL
                }
            }
        }
        return invariant

    }

    fun transformTrace(ts: SymbolicTrace, origSys: UppaalSystem): SymbolicTrace {
        var prev: SymbolicState? = null
        val result = SymbolicTrace()
        val it = ts.iterator()
        while (it.hasNext()) {
            val curr = it.next()
            val edgesWs = curr.edges
            if (prev != null) {
                for (i in edgesWs.indices) {
                    val edgeWs = edgesWs[i]
                    val origProccess = origSys.getProcess(edgeWs.process.index)
                    val origEdge = origProccess.getEdge(edgeWs.index)

                    edgesWs[i] = SystemEdgeSelect(origEdge, edgeWs.selectList)
                }
            }
            var transTarget: SymbolicState

            var locations: Array<SystemLocation>

            transTarget = curr.target
            locations = transTarget.locations
            for (i in locations.indices) {
                locations[i] = origSys.getProcess(i).getLocation(locations[i].index)
            }

            val origTarget = SymbolicState(locations, transTarget.variableValues,
                    transTarget.polyhedron)
            result.add(SymbolicTransition(prev, edgesWs, origTarget))
            prev = origTarget
        }
        return result
    }

    private val classToBeginString = mapOf(GlobalDeclarations::class to "<declaration>",
            org.muml.uppaal.templates.AbstractTemplate::class to "<parameter>",
            LocalDeclarations::class to "<declaration>",
            SystemDeclarations::class to "<system>")

    fun buildProblem(obj: EObject, description: String): Problem {
        var startTag: String? = null
        val ancestry = generateSequence(obj) { it.eContainer() }
        val container = ancestry.first {
            classToBeginString.keys.any { k ->
                val result = k.isInstance(it)
                startTag = classToBeginString[k]
                result
            }
        }
        if (startTag == null) {
            throw Exception("No xml container")
        }
        val containerNode = NodeModelUtils.getNode(container)
        val startTagNode = containerNode.children.first { it.text == startTag }

        val node = NodeModelUtils.getNode(obj)
        val containerNodeBeginOffset = containerNode.totalOffset
        val blockBeginOffset = startTagNode.endOffset - containerNodeBeginOffset
        val varEndOffset = node.endOffset - containerNodeBeginOffset

        val subString = containerNode.text.subSequence(blockBeginOffset, varEndOffset)
        var line = 1
        var column = 1
        var offsetToCheck = node.offset - startTagNode.endOffset
        var beginLineAndColumn: LineAndColumn? = null
        run loop@{
            subString.forEachIndexed { i, it ->
                if (i == offsetToCheck) {
                    if (beginLineAndColumn != null) {
                        return@loop
                    } else {
                        beginLineAndColumn = LineAndColumn.from(line, column)
                        offsetToCheck = node.endOffset - startTagNode.endOffset
                        line = 1
                        column = 1
                    }
                }
                if (it == '\n') {
                    line++
                    column = 1
                } else {
                    column++
                }
            }
        }
        var currentXpath = ""
        ancestry.forEach {
            currentXpath = when (it) {
                is GlobalDeclarations -> "/declaration"
                is NSTA -> "/nta"
                is Parameter -> "/parameter"
                is Template -> "/template[${(it.eContainer() as NSTA).template.indexOf(it) + 1}]"
                is Expression -> ""
                is Variable -> ""
                is Declaration -> ""
                else -> TODO("$it")
            } + currentXpath
        }

        return Problem("error", currentXpath, beginLineAndColumn!!.line, beginLineAndColumn!!.column, line, column, description)
    }
}
