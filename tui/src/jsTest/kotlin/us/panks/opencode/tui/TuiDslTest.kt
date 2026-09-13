package us.panks.opencode.tui

import js.promise.Promise
import us.panks.opencode.annotations.OpenCodeTuiPlugin
import us.panks.opencode.annotations.TuiStart
import kotlin.test.Test
import kotlin.test.assertEquals

@OpenCodeTuiPlugin(
    id = "test.annotated-tui",
    entryPoint = "createAnnotatedTuiFixture",
)
internal class AnnotatedTuiFixture {
    @TuiStart
    suspend fun start(context: TuiContext) {
        context.toast("Annotated ready", variant = ToastVariants.INFO)
    }
}

class TuiDslTest {
    @Test
    fun generatedTuiEntrypointRunsAnnotatedStart(): Promise<Unit> {
        var shown: dynamic = null
        val toast: dynamic = js("({})")
        toast.show = { options: dynamic -> shown = options }
        val ui: dynamic = js("({})")
        ui.toast = toast
        val context: dynamic = js("({})")
        context.ui = ui

        val definition: dynamic = createAnnotatedTuiFixture()
        return definition.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .then<Unit> {
                assertEquals("test.annotated-tui", definition.id as String)
                assertEquals("Annotated ready", shown.message as String)
                assertEquals("info", shown.variant as String)
            }
    }

    @Test
    fun createsTypedAsyncTuiPlugin(): Promise<Unit> {
        val definition = tuiPlugin("example.async-tui") { Unit }
        val context: dynamic = js("({})")
        return definition.setup(context)
            .unsafeCast<Promise<Unit>>()
            .then<Unit> {
                assertEquals("example.async-tui", definition.id)
            }
    }

    @Test
    fun preservesCleanupFromAsyncTuiSetup(): Promise<Unit> {
        var disposed = false
        val definition = tuiPluginWithCleanup("example.cleanup-tui") {
            { disposed = true }
        }
        val context: dynamic = js("({})")
        return definition.setup(context)
            .unsafeCast<Promise<dynamic>>()
            .flatThen { cleanup ->
                val cleanupResult: dynamic = cleanup()
                assertEquals("function", jsTypeOf(cleanupResult.then))
                cleanupResult.unsafeCast<Promise<Unit>>()
            }
            .then<Unit> {
                assertEquals(true, disposed)
            }
    }

    @Test
    fun createsTuiPluginAndShowsToast(): Promise<Unit> {
        var shown: dynamic = null
        val definition = tuiPlugin("example.tui") { context ->
            context.toast(
                message = "Ready",
                title = "Guard",
                variant = ToastVariants.SUCCESS,
                duration = 2500.0,
            )
        }

        val toast: dynamic = js("({})")
        toast.show = { options: dynamic -> shown = options }
        val ui: dynamic = js("({})")
        ui.toast = toast
        val context: dynamic = js("({})")
        context.ui = ui

        return definition.setup(context)
            .unsafeCast<Promise<Unit>>()
            .then<Unit> {
                assertEquals("example.tui", definition.id)
                assertEquals("Ready", shown.message as String)
                assertEquals("Guard", shown.title as String)
                assertEquals("success", shown.variant as String)
                assertEquals(2500.0, shown.duration as Double)
            }
    }

    @Test
    fun buildsDialogOptionsAndPreservesResults(): Promise<Unit> {
        var alertOptions: dynamic = null
        var confirmOptions: dynamic = null
        var promptOptions: dynamic = null
        var selectOptions: dynamic = null
        val dialog: dynamic = js("({})")
        dialog.alert = { options: dynamic ->
            alertOptions = options
            Promise.resolve<Any?>(Unit)
        }
        dialog.confirm = { options: dynamic ->
            confirmOptions = options
            Promise.resolve<Boolean?>(true)
        }
        dialog.prompt = { options: dynamic ->
            promptOptions = options
            Promise.resolve<String?>(null)
        }
        dialog.select = { options: dynamic ->
            selectOptions = options
            Promise.resolve<String?>("safe")
        }
        val ui: dynamic = js("({})")
        ui.dialog = dialog
        val rawContext: dynamic = js("({})")
        rawContext.ui = ui
        val context = rawContext.unsafeCast<TuiContext>()

        val alert: Promise<Unit> = context.alert("Warning", "Sensitive path")
        return alert
            .flatThen { context.confirm("Continue", "Proceed?", "Yes", "No") }
            .flatThen { confirmed ->
                assertEquals(true, confirmed)
                context.prompt("Path", placeholder = "/tmp/file")
            }
            .flatThen { prompted ->
                assertEquals(null, prompted)
                context.select(
                    title = "Mode",
                    options = listOf(
                        TuiSelectOption("Safe", "safe", description = "Recommended"),
                        TuiSelectOption("Fast", "fast", disabled = true),
                    ),
                    current = "safe",
                )
            }
            .then<Unit> { selected ->
                assertEquals("safe", selected)
                assertEquals("Warning", alertOptions.title as String)
                assertEquals("Yes", confirmOptions.label.confirm as String)
                assertEquals("No", confirmOptions.label.cancel as String)
                assertEquals("/tmp/file", promptOptions.placeholder as String)
                assertEquals(2, selectOptions.options.length as Int)
                assertEquals("Recommended", selectOptions.options[0].description as String)
                assertEquals(true, selectOptions.options[1].disabled as Boolean)
            }
    }
}
