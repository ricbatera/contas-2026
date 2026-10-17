package br.com.rdrtech.ia.dto;

import jakarta.validation.constraints.Size;

public record TesteIaRequest(@Size(max = 4000) String prompt) {
}
