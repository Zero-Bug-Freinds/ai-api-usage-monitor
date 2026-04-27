import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { BillingEventsModule } from './billing-events/billing-events.module';
import { InAppNotificationsModule } from './in-app-notifications/in-app-notifications.module';
import { PrismaModule } from './prisma/prisma.module';
import { TeamEventsModule } from './team-events/team-events.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env.local', '.env'],
    }),
    PrismaModule,
    InAppNotificationsModule,
    TeamEventsModule,
    BillingEventsModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
