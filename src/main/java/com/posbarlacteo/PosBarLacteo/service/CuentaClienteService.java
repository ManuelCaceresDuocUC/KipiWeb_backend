package com.posbarlacteo.PosBarLacteo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.posbarlacteo.PosBarLacteo.model.Cliente;
import com.posbarlacteo.PosBarLacteo.model.MovimientoCliente;
import com.posbarlacteo.PosBarLacteo.repository.ClienteRepository;
import com.posbarlacteo.PosBarLacteo.repository.MovimientoClienteRepository;
import com.posbarlacteo.PosBarLacteo.security.AuthPrincipal;

/** Estado de cuenta del cliente: deja constancia de cada cambio de su deuda con el saldo real resultante. */
@Service
public class CuentaClienteService {

    private final ClienteRepository clienteRepository;
    private final MovimientoClienteRepository movimientos;

    public CuentaClienteService(ClienteRepository clienteRepository, MovimientoClienteRepository movimientos) {
        this.clienteRepository = clienteRepository;
        this.movimientos = movimientos;
    }

    /** Debe llamarse DESPUÉS del UPDATE atómico de la deuda: lee el saldo que quedó en la base. */
    @Transactional
    public void registrar(Cliente cliente, AuthPrincipal p, String tipo, double montoConSigno,
                          Long ventaId, String metodo, String motivo) {
        MovimientoCliente m = new MovimientoCliente();
        m.setCliente(cliente);
        m.setEmpresa(cliente.getEmpresa());
        m.setTipo(tipo);
        m.setMonto(montoConSigno);
        Double saldo = clienteRepository.saldoActual(cliente.getId(), p.empresaId());
        m.setSaldoResultante(saldo != null ? saldo : 0.0);
        m.setVentaId(ventaId);
        m.setMetodoPago(metodo);
        m.setMotivo(motivo);
        m.setUsuarioNombre(p.usuario());
        movimientos.save(m);
    }
}
