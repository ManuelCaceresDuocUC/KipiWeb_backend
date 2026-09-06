package com.posbarlacteo.PosBarLacteo.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.posbarlacteo.PosBarLacteo.model.Empresa;
import com.posbarlacteo.PosBarLacteo.repository.EmpresaRepository;

// Importaciones SDK Mercado Pago v3.7.0
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Value("${mercadopago.access.token}")
    private String mpAccessToken;

    @PostMapping("/mercadopago")
    public ResponseEntity<String> recibirNotificacion(@RequestBody JsonNode payload) {
        try {
            // Mercado Pago requiere que respondas HTTP 200 lo más rápido posible
            // Verificamos si la notificación es de un "payment" (pago)
            if (payload.has("type") && "payment".equals(payload.get("type").asText())) {
                
                String paymentId = payload.get("data").get("id").asText();
                
                MercadoPagoConfig.setAccessToken(mpAccessToken);
                PaymentClient paymentClient = new PaymentClient();
                
                // Consultamos a la API de MP el estado real de este pago por seguridad
                Payment payment = paymentClient.get(Long.parseLong(paymentId));
                
                // Si el pago está aprobado, activamos la empresa
                if ("approved".equals(payment.getStatus())) {
                    
                    // Recuperamos el ID de la empresa que enviamos en "external_reference"
                    String idEmpresaStr = payment.getExternalReference();
                    
                    if (idEmpresaStr != null) {
                        Long empresaId = Long.parseLong(idEmpresaStr);
                        Optional<Empresa> empresaOpt = empresaRepository.findById(empresaId);
                        
                        if (empresaOpt.isPresent()) {
                            Empresa empresa = empresaOpt.get();
                            empresa.setActivo(true); // ¡Activamos la empresa!
                            empresaRepository.save(empresa);
                            System.out.println("Empresa " + empresaId + " activada exitosamente tras pago.");
                        }
                    }
                }
            }
            
            return ResponseEntity.status(HttpStatus.OK).body("Notificación procesada");

        } catch (MPApiException | MPException mpEx) {
            System.err.println("Error consultando Mercado Pago en Webhook: " + mpEx.getMessage());
            // Retornamos OK de todos modos para evitar reintentos infinitos si el error es de formato
            return ResponseEntity.status(HttpStatus.OK).body("Error procesando pago con la pasarela");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.OK).body("Error procesando notificación");
        }
    }
}