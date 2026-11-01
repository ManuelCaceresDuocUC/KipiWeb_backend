package com.posbarlacteo.PosBarLacteo.model;

import com.posbarlacteo.PosBarLacteo.util.CifradoClaves;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Cifra al escribir y descifra al leer. Los valores antiguos sin cifrar se leen igual (compatibilidad). */
@Converter
public class ClaveCifradaConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String atributo) {
        if (atributo == null || atributo.isBlank() || CifradoClaves.estaCifrado(atributo)) {
            return atributo;
        }
        return CifradoClaves.cifrar(atributo);
    }

    @Override
    public String convertToEntityAttribute(String columna) {
        return columna == null ? null : CifradoClaves.descifrar(columna);
    }
}
