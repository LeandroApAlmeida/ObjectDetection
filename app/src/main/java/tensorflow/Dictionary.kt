package tensorflow

import android.content.Context
import java.io.BufferedReader

/**
 * Dicionário para tradução de termos do inglês para o português.
 */
class Dictionary {

    /**Pares: Chave = termo em inglês, valor = termo em português.*/
    private var map: MutableMap<String, String> = mutableMapOf()

    /**
     * Carrega o arquivo com o dicionário que está em assets com o nome dictionary-pt-br.dat.
     */
    constructor(context: Context) {
        // Aqui o processamento é o seguinte:
        //
        // Primeiramente é feita a leitura do conteúdo do arquivo "dictionary-pt-br.dat" para a
        // variável text.
        //
        // O formato do texto do arquivo é:
        //
        //     termo_em_inglês1=termo_em_português1
        //     termo_em_inglês2=termo_em_português2
        //     termo_em_inglês3=termo_em_português3
        //
        //     ...
        //
        //     termo_em_inglês_n=termo_em_português_n
        //
        // A quebra de linha é feita com os caracteres \r\n.
        //
        // Primeiramente faz-se a recuperação de todas as linhas do arquivo, logo após, obtém-se
        // o termo em inglês e seu respectivo significado em português e adiciona-os ao map.
        try {
            val text = context
                .assets
                .open("dictionary-pt-br.dat")
                .bufferedReader()
                .use(BufferedReader::readText)

            val lines = text.split("\r\n")
            for (line in lines) {
                val words = line.split("=")
                map[words[0]] = words[1]
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Traduz um termo do inglês para o português.
     * @param englishLabel termo em inglês.
     * @return o termo traduduzido para o português, ou retorna em inglês, caso não tenha
     * entrada no dicionário correspondente.
     */
    fun translateToPortuguese(englishLabel: String): String {
        return if (map.containsKey(englishLabel)) {
            map[englishLabel]!!
        } else {
            englishLabel
        }
    }

}