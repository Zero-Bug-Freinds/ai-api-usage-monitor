import { Module } from '@nestjs/common';
import { PrismaModule } from '../prisma/prisma.module';
import { IdentityExternalApiKeyEventsConsumer } from './identity-external-api-key-events.consumer';
import { IdentityExternalApiKeyInAppHandlerService } from './identity-external-api-key-in-app-handler.service';

@Module({
  imports: [PrismaModule],
  providers: [IdentityExternalApiKeyInAppHandlerService, IdentityExternalApiKeyEventsConsumer],
})
export class IdentityExternalApiKeyEventsModule {}
