package za.co.boardaf

import android.app.Application
import org.junit.Test

class BoardViewModelFactoryContractTest {

    @Test
    fun exposesApplicationConstructorRequiredByDefaultFactory() {
        BoardViewModel::class.java.getConstructor(Application::class.java)
    }
}
