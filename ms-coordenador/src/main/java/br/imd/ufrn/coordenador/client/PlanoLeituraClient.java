package br.imd.ufrn.coordenador.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

import br.imd.ufrn.coordenador.dto.ChatRequest;
import br.imd.ufrn.coordenador.dto.ChatResponse;

public interface PlanoLeituraClient {

    @PostExchange("/plano")
    ChatResponse.PlanoLeituraDto gerar(@RequestBody ChatRequest.LivroSelecionado request);
}
