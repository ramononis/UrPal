package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import java.awt.Color
import java.io.PrintStream
import java.util.HashSet
import java.util.concurrent.atomic.AtomicInteger

import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

import com.uppaal.engine.QueryResult
import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.UppaalUtil

@SanityCheck(name = "Deadlocks")
class DeadlockProperty : AbstractProperty() {

    override fun doCheck(nsta: NSTA, doc: Document, sys: UppaalSystem, cb: (SanityCheckResult) -> Unit) {
        val cbs = HashSet<() -> Unit>()
        val i = AtomicInteger()
        val locs = sys.processes.flatMap { p ->
            p.locations.filter { l ->
                val result = p.edges.none { e -> e.edge.source == l.location }
                if (result && l.name.isNullOrEmpty()) {
                    l.location.setProperty("name", "__l${i.getAndIncrement()}")
                    cbs.add { l.location.setProperty("name", null) }
                }
                result
            }
        }
        val query = if (locs.isEmpty()) {
            "A[] (!deadlock)"
        } else {
            "A[] (deadlock imply ${locs.joinToString(" or ")})"
        }
        AbstractProperty.engineQuery(sys, query, "trace 1") { qr, t ->
            cb(object : SanityCheckResult() {

                override fun write(out: PrintStream, err: PrintStream) {
                    if (qr.status == QueryResult.OK) {
                        out.println("No unwanted deadlocks found!")
                    } else {
                        err.println("Unwanted deadlocks found! See trace below:")
                        t.subList(1, t.size).forEach { System.err.println(it.traceFormat()) }
                    }
                }

                override fun toPanel(): JPanel {
                    val p = JPanel()
                    p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
                    if (qr.status == QueryResult.OK) {
                        val label = JLabel("No unwanted deadlocks!")
                        p.add(label)
                    } else {

                        val label = JLabel("Unwanted deadlocks found! Click button below to load trace:")
                        label.foreground = Color.RED
                        p.add(label)
                        val button = JButton("Load trace")
                        button.addActionListener {
                            val sim = UppaalUtil.getSystemInspector(button)!!.simulator
                            sim.uppaalSystem.set(sys)
                            sim.a(t, 0)
                        }
                        p.add(button)
                    }
                    return p
                }
            })
            cbs.forEach { it() }
        }

    }

}
