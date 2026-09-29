package br.com.curso.masterpokedex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.curso.masterpokedex.data.network.PokeApiService
import br.com.curso.masterpokedex.data.network.PokemonBrief
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MasterPokedexTheme {
                PokedexApp()
            }
        }
    }
}

/**
 * Tema customizado com paleta Pokédex (vermelho + verde neon)
 */
@Composable
fun MasterPokedexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF1744), // Vermelho Pokedex
            secondary = Color(0xFF00E676),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}

class PokedexViewModel : ViewModel() {
    // Instância do Retrofit com baseUrl e conversor GSON
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://pokeapi.co/api/v2/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Expõe o service para ser chamado pela UI
    val apiService: PokeApiService = retrofit.create(PokeApiService::class.java)

    // Estados reativos que a UI observa
    var pokemonList by mutableStateOf<List<PokemonBrief>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Busca a lista de Pokémons; chamada pelo LaunchedEffect e pelo botão "Tentar novamente"
    suspend fun carregarPokemons() {
        isLoading = true
        error = null
        try {
            val response = apiService.getList()
            pokemonList = response.results
        } catch (e: CancellationException) {
            throw e // nunca engolir cancelamento de coroutine
        } catch (e: Exception) {
            error = "Não foi possível carregar os Pokémons. Verifique sua conexão."
        } finally {
            isLoading = false
        }
    }
}

/**
 * Tela principal da Pokédex com gerenciamento de estados de rede e grade de cartões.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexApp(viewModel: PokedexViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()

    // Executa a requisição uma única vez ao entrar na tela
    LaunchedEffect(Unit) {
        viewModel.carregarPokemons()
    }

    // Gradiente vermelho escuro → preto
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF8E0E00), Color(0xFF1F1C1C))
    )

    Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "MASTER POKÉDEX",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when {
                    // Estado: carregando
                    viewModel.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFFF1744)
                    )
                    // Estado: erro de rede
                    viewModel.error != null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Erro: ${viewModel.error}",
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { coroutineScope.launch { viewModel.carregarPokemons() } }) {
                            Text("Tentar novamente")
                        }
                    }
                    // Estado: lista carregada
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(viewModel.pokemonList) { pokemon ->
                            PokemonCard(pokemon)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cartão individual de Pokémon com imagem oficial e número formatado
 */
@Composable
fun PokemonCard(pokemon: PokemonBrief) {
    // Extrai o ID numérico da URL: "https://pokeapi.co/api/v2/pokemon/1/" -> 1
    // Se a URL vier em formato inesperado, id fica null em vez de assumir "1" na cara-dura
    val id = pokemon.url.trimEnd('/').substringAfterLast('/').toIntOrNull()
    val imageUrl = id?.let {
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$it.png"
    }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Carrega a arte oficial diretamente da nuvem (Coil gerencia cache)
            // placeholder/error evitam um espaço em branco caso a imagem falhe ou o id seja nulo
            AsyncImage(
                model = imageUrl,
                contentDescription = pokemon.name,
                placeholder = ColorPainter(Color.White.copy(alpha = 0.1f)),
                error = ColorPainter(Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pokemon.name.uppercase(),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = id?.let { "#${it.toString().padStart(3, '0')}" } ?: "#???",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}