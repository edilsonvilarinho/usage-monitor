package com.usagemonitor.presentation.ui.theme

/**
 * O emoji que o usuário escolhe para uma conta Claude (issue #287).
 *
 * Duas contas Claude no notch mostram o mesmo asterisco, e a cor da #275 só as
 * separa para quem lembra qual tom é de qual conta. O emoji fica no canto do
 * anel e ao lado do título, e diz a conta de relance.
 *
 * **Conjunto fixo, não campo livre** — o mesmo raciocínio da paleta de
 * [AccountAccent]. Cada glifo aqui é um code point só, com apresentação de
 * emoji por padrão e sem seletor de variação, e foi visto renderizado em cor
 * pelo Compose no Windows, na fonte mono e offscreen. Um campo livre traria
 * sequências ZWJ, tons de pele e bandeiras, de largura imprevisível e com o
 * risco de virar quadrado vazio onde falta a fonte.
 *
 * **É conteúdo escolhido pelo usuário, não cromo**: a regra "sem emoji" do
 * design system vale para a interface, e este glifo é da mesma natureza do
 * apelido — ele identifica a conta, e o nome continua escrito ao lado.
 *
 * O [name] é o que vai para o disco: renomear um valor apaga a escolha de quem o
 * tinha, e a leitura cai em "Nenhum".
 */
enum class AccountEmoji(
    val glyph: String,
    val labelPt: String,
    val labelEn: String
) {
    FOX("🦊", "Raposa", "Fox"),
    CAT("🐱", "Gato", "Cat"),
    DOG("🐶", "Cachorro", "Dog"),
    PANDA("🐼", "Panda", "Panda"),
    OWL("🦉", "Coruja", "Owl"),
    OCTOPUS("🐙", "Polvo", "Octopus"),
    BEE("🐝", "Abelha", "Bee"),
    UNICORN("🦄", "Unicórnio", "Unicorn"),
    ROCKET("🚀", "Foguete", "Rocket"),
    STAR("⭐", "Estrela", "Star"),
    FIRE("🔥", "Fogo", "Fire"),
    BOLT("⚡", "Raio", "Bolt"),
    CLOVER("🍀", "Trevo", "Clover"),
    MOON("🌙", "Lua", "Moon"),
    BRIEFCASE("💼", "Maleta", "Briefcase"),
    HOUSE("🏠", "Casa", "House");

    fun label(isPt: Boolean): String = if (isPt) labelPt else labelEn

    companion object {
        /** A escolha lida do disco; nome desconhecido é "Nenhum", nunca erro. */
        fun fromStorage(value: String?): AccountEmoji? =
            value?.let { name -> entries.firstOrNull { entry -> entry.name == name } }
    }
}
