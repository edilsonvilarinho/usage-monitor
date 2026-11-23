// Validação do roteiro (scenes.json) antes de sintetizar ou renderizar: regras de câmera e foco das cenas screen.
// MAX_ZOOM espelha a constante de assets/video.html (o teste confere); a câmera o aplica ao desenhar.

export const MAX_ZOOM = 1.35
// Intervalo mínimo entre duas chaves de câmera: abaixo disso o movimento vira solavanco ("cortaram demais").
export const MIN_KEY_GAP = 1.5

const seconds = (value) => value.toFixed(1).replace('.', ',')

// Lista de problemas de uma cena (vazia se estiver certa). Mensagens em português, com o id da cena.
export function sceneProblems(scene) {
    const problems = []
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
    if (scene.focusFor !== undefined && !(scene.focusFor > 0)) {
        problems.push(`${scene.id}: focusFor ${scene.focusFor} precisa ser maior que 0 s`)
    }
    return problems
}

export function assertScenes(scenes) {
    const problems = scenes.flatMap(sceneProblems)
    if (problems.length > 0) throw new Error(`Roteiro recusado:\n${problems.join('\n')}`)
}
