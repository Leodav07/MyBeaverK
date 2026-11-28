package com.example.mybeaver

import com.example.mybeaver.sql.ScriptChunk
import com.example.mybeaver.sql.SqlScriptSplitter
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlScriptSplitterTest {

    private fun sqls(script: String) = SqlScriptSplitter.split(script).map { it.sql }

    @Test
    fun `separa por punto y coma`() {
        assertEquals(listOf("SELECT 1", "SELECT 2"), sqls("SELECT 1; SELECT 2;"))
    }

    @Test
    fun `no corta dentro de textos ni comentarios`() {
        val script = "INSERT INTO t VALUES ('a;b', \"c;d\", `e;f`); -- comentario; con punto y coma\nSELECT 'it''s; ok'; /* ; */ SELECT 3"
        assertEquals(
            listOf("INSERT INTO t VALUES ('a;b', \"c;d\", `e;f`)", "-- comentario; con punto y coma\nSELECT 'it''s; ok'", "/* ; */ SELECT 3"),
            sqls(script)
        )
    }

    @Test
    fun `ignora sentencias vacias y solo comentarios`() {
        assertEquals(listOf("-- nada\n SELECT 1"), sqls(";;  -- nada\n SELECT 1 ;  /* fin */"))
    }

    @Test
    fun `entiende DELIMITER`() {
        val script = """
            DROP PROCEDURE IF EXISTS p;
            DELIMITER $$
            CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END$$
            DELIMITER ;
            CALL p();
        """.trimIndent()
        assertEquals(
            listOf("DROP PROCEDURE IF EXISTS p", "CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END", "CALL p()"),
            sqls(script)
        )
    }

    @Test
    fun `procedimiento sin DELIMITER se manda entero al servidor`() {
        val script = "CREATE TABLE x (a INT);\nCREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END;\nCALL p();"
        assertEquals(
            listOf(
                ScriptChunk("CREATE TABLE x (a INT)"),
                ScriptChunk("CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END;\nCALL p()", multi = true)
            ),
            SqlScriptSplitter.split(script)
        )
    }
}
