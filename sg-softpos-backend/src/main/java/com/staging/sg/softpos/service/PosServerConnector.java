package com.staging.sg.softpos.service;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import com.staging.sg.softpos.domain.*;

public interface PosServerConnector {
    PosServerMode mode();
    PosServerPaymentResult exchange(PosServerPaymentCommand command, SoftPosPosServerRoute route) throws Exception;
}
