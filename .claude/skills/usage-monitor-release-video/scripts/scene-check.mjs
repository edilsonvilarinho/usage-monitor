// Validação do roteiro (scenes.json) antes de sintetizar ou renderizar: regras de câmera e foco das cenas screen.
// MAX_ZOOM espelha a constante de assets/video.html (o teste confere); a câmera o aplica ao desenhar.

export const MAX_ZOOM = 1.35
// Intervalo mínimo entre duas chaves de câmera: abaixo disso o movimento vira solavanco ("cortaram demais").
export const MIN_KEY_GAP = 1.5

// Zoom máximo de uma cena: o dela (`maxZoom`, 1 a 3) ou o padrão. A câmera em video.html aplica o mesmo valor.
export const MAX_ZOOM_CEILING = 3
export const zoomLimit = (scene) => scene.maxZoom ?? MAX_ZOOM

const seconds = (value) => value.toFixed(1).replace('.', ',')

// Lista de problemas de uma cena (vazia se estiver certa). Mensagens em português, com o id da cena.
export function sceneProblems(scene) {
    const problems = []
    if (scene.maxZoom !== undefined &&
        !(typeof scene.maxZoom === 'number' && scene.maxZoom >= 1 && scene.maxZoom <= MAX_ZOOM_CEILING)) {
        problems.push(`${scene.id}: maxZoom ${scene.maxZoom} precisa ser um número de 1 a ${MAX_ZOOM_CEILING}`)
    }
    if (scene.camera) {
        const times = scene.camera.map((key) => key.at).sort((a, b) => a - b)
        for (let index = 0; index < times.length - 1; index += 1) {
            const gap = times[index + 1] - times[index]
            if (gap < MIN_KEY_GAP - 1e-9) {
                problems.push(`${scene.id}: chaves de câmera em ${seconds(times[index])} s e ${seconds(times[index + 1])} s ` +
                    `ficam a ${seconds(gap)} s uma da outra; o mínimo é ${seconds(MIN_KEY_GAP)} s`)
            }
        }
    }
    // Falas por momento: lines[lang] pode ser uma lista [{ at, text, say? }]; precisam ter texto e crescer em `at`.
    for (const [language, line] of Object.entries(scene.lines ?? {})) {
        if (!Array.isArray(line)) continue
        line.forEach((beat, index) => {
            if (typeof beat.text !== 'string' || beat.text.trim() === '') {
                problems.push(`${scene.id}: fala ${index + 1} (${language}) sem texto`)
            }
            if (index > 0 && (beat.at ?? 0) <= (line[index - 1].at ?? 0)) {
                problems.push(`${scene.id}: falas (${language}) fora de ordem: a fala ${index + 1} em ${seconds(beat.at ?? 0)} s ` +
                    `não vem depois da fala ${index} em ${seconds(line[index - 1].at ?? 0)} s`)
            }
        })
    }
    if (scene.focusFor !== undefined && !(scene.focusFor > 0)) {
        problems.push(`${scene.id}: focusFor ${scene.focusFor} precisa ser maior que 0 s`)
    }
    return problems
}

export function assertScenes(scenes) {
    const problems = scenes.flatMap(sceneProblems)
    if (problems.length > 0) throw new Error(`Roteiro recusado:\n${problems.join('\n')}`)
}
