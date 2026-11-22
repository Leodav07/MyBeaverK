package com.example.mybeaver.storage
import com.example.mybeaver.MybeaverApplication
import com.example.mybeaver.models.ConnectionModel
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.File


@Component
class ConnectionStorage {

    private val mapper = jacksonObjectMapper()
    private val file = File("data/connections.json")

    fun loadConnections(): MutableList<ConnectionModel> {
        file.parentFile?.mkdirs()

        if (file.createNewFile()) {
            val emptyList = mutableListOf<ConnectionModel>()
            mapper.writeValue(file, emptyList)
            return emptyList
        }

        return mapper.readValue(file)
    }

    fun addConnection(newConnection: ConnectionModel) : Boolean{
        val currentList = loadConnections()
        val newId = newConnection.id

        if(searchConnection(newId)!=null){
            return false
        }

       val ok = currentList.add(newConnection)
        if(ok) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, currentList)
            return true
        }else{
            return false
        }

    }

    fun removeConnection(id: Int) : Boolean{
        val currentList = loadConnections()
        val ok = currentList.removeIf { it.id == id }

        if(ok){
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, currentList)
            return true
        }else{
            return false
        }


    }

    fun searchConnection(id: Int?): ConnectionModel?{
        val currentList = loadConnections()
        return currentList.find { it.id == id }
    }





}

