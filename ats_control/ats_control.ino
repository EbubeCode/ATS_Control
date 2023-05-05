
/*model select
      //ACS712
      0-"ACS712ELCTR-05B",
      1-"ACS712ELCTR-20A",
      2-"ACS712ELCTR-30A",
      
      //ACS758
      3,// for ACS758LCB-050B
      4,// for ACS758LCB-050U
      5,// for ACS758LCB-100B
      6,// for ACS758LCB-100U
      7,// for ACS758KCB-150B
      8,// for ACS758KCB-150U
      9,// for ACS758ECB-200B
      10,// for ACS758ECB-200U 
      
      ///ACS770
      11,// for ACS770x-050B ///      
      12,// for ACS770x-050U  
      13,// for ACS770x-100B
      14,// for ACS770x-100U
      15,// for ACS770x-150B
      16,// for ACS770x-150U
      17,// for ACS770x-200B  
      18,// for ACS770x-200U  
      

      19 for "ACS732KLATR-20AB",
      20 for "ACS732KLATR-40AB",
      21 for "ACS732KLATR-65AB", 
      22 for "ACS732KLATR-65AU",   
      23 for "ACS732KLATR-75AB", 

      24 for "ACS733KLATR-20AB",
      25 for "ACS733KLATR-40AB",
      26 for "ACS733KLATR-40AU", 
      27 for "ACS733KLATR-65AU",
      */
#include <LiquidCrystal_I2C.h>
LiquidCrystal_I2C lcd(0x27, 20, 4);
#include <Robojax_AllegroACS_Current_Sensor.h>
#include <Wire.h>


//*************************measuring current using ACS712***********************
const int VIN = A1;      // define the Arduino pin A0 as voltage input (V in)
const float VCC = 5.04;  // supply voltage
const int MODEL = 2;     // enter the model (see above list)
Robojax_AllegroACS_Current_Sensor robojax(MODEL, VIN);

//**************************Measuring Voltage using Stepdown transformer
int load_volt_sensor = A0;   //Analog Input
float load_vd = 0.0;         //Voltage In after voltage divider
float load_rect_volt = 0.0;  //Actual voltage after calculation
float CalVal = 11.00;        //Voltage divider calibration value
float transformer_turn_ratio = 19.052;
float load_trans_sec_volt = 0.0;
int load_trans_pri_volt = 0.0;
float rms_voltage_factor = 1.4;

//*****************************Timing***************************
const long interval = 200;  //Interval to read voltages


//***************************input pin declearation********************
#define waterLevel A2          // water level sensor
const int batteryCharger = 2;  // battey charger to move from pv to load current
const int tripSwitch = 3;      // trip switch to ensure no tamper when device in operation
const int echoPin = 11;        // utrasonic echo pin
const int grid_line_input = 12;
const int gen_line_input = 13;

//***************************output pin declearation********************
const int grid_line_output = 4;
const int solar_line_output = 5;
const int gen_line_output = 6;
const int gen_on_switch = 7;
const int gen_start_switch = 8;
const int buzzer = 9;
const int trigPin = 10;  // utrasonic trigger pin

//**********variable for reading input phase status. This is used to keeping all input at "0" zero or off state****************
bool grid_state = false;
bool gen_state = false;
bool solar_state = false;
bool all_source_state = false;
bool solaralarmstate = false;
int state = -1;

//****************************Variables required to make communication with app possible***************************************
bool autoStartGen = true;
char bluetoothCommand;

void setup() {
  pinMode(grid_line_input, INPUT);
  pinMode(gen_line_input, INPUT);
  pinMode(echoPin, INPUT);
  pinMode(tripSwitch, INPUT);

  pinMode(batteryCharger, OUTPUT);
  pinMode(trigPin, OUTPUT);
  pinMode(grid_line_output, OUTPUT);
  pinMode(gen_line_output, OUTPUT);
  pinMode(solar_line_output, OUTPUT);
  pinMode(gen_on_switch, OUTPUT);
  pinMode(gen_start_switch, OUTPUT);
  pinMode(buzzer, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);

  pinMode(A0, INPUT);
  pinMode(A1, INPUT);
  pinMode(waterLevel, INPUT);

  // PLEASE NOTE ALL RELAY USED IN THIS PROJECT ARE ACTIVE LOW
  digitalWrite(batteryCharger, HIGH);
  digitalWrite(grid_line_output, LOW);
  digitalWrite(gen_line_output, LOW);
  digitalWrite(solar_line_output, LOW);
  digitalWrite(gen_on_switch, LOW);
  digitalWrite(gen_start_switch, LOW);
  digitalWrite(buzzer, LOW);

  //********************Initialise serial for bluetooth communication****************
  Serial.begin(9600);

  //***************initialize the LCD*******************
  lcd.begin();
  lcd.backlight();
  lcd.print("3 POWER SOURCE ATS");
  lcd.setCursor(0, 1);
  lcd.print("    SYSTEM");
  delay(125);
  lcd.setCursor(0, 2);
  lcd.print("Initializing...");
  lcd.blink();
  delay(250);
}

void turnOnBuzzer();
void toggleGen(int toggle);
bool checkGenState();

void loop() {

  if (digitalRead(tripSwitch) == HIGH) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print(" Device is tampared ");
    lcd.setCursor(0, 1);
    lcd.print("Output is turned off");
    digitalWrite(gen_line_output, LOW);
    digitalWrite(solar_line_output, LOW);
    digitalWrite(grid_line_output, LOW);
    return;
  }

  //**********************checking for the state of each input******************************
  int gridline_current_state = digitalRead(grid_line_input);
  int genline_current_state = digitalRead(gen_line_input);

  //***************************Measure Voltage*******************************************
  load_volt_sensor = analogRead(A0);                                     //reading the sensor values in 10bit resolution 0-1023
  load_vd = (load_volt_sensor * 5.00) / 1024.00;                         //Convert 10bit input to an actual voltage
  load_rect_volt = (load_vd - 0.038) * CalVal;                           //calculate voltage divider and calibration value to get output dc voltage
  load_trans_sec_volt = (load_rect_volt / rms_voltage_factor);           //reconvert DC voltage to AC voltage from transformer secondary turn
  load_trans_pri_volt = (load_trans_sec_volt * transformer_turn_ratio);  //calculating the actual AC MAIN voltage for this source

  //************************* Measure Power ******************************************
  int current = robojax.getCurrentAverage(300);
  int Power = (load_trans_pri_volt * current);

  // ***********************Output voltage and current lcd info************************
  lcd.setCursor(0, 1);
  lcd.print("Voltage: ");
  lcd.print(load_trans_pri_volt);
  lcd.print("V  ");                                                  // unit for voltage to be measured
  Serial.println("voltage = " + String(load_trans_pri_volt) + "V");  // send voltage to bluetooth app
  lcd.print("Current: ");
  lcd.print(current, 3);
  lcd.print("A");                                        //unit for the current to be measured
  Serial.println("current = " + String(current) + "A");  // send current to bluetooth app
  lcd.setCursor(0, 2);
  lcd.print("Power: ");
  lcd.print(Power);
  lcd.print("W");                                    //unit for the current to be measured
  Serial.println("power = " + String(Power) + "W");  // send power to bluetooth app
  if (autoStartGen) {
    lcd.setCursor(0, 3);
    lcd.print("Auto start gen: ON");
    Serial.println("auto start = 1");
  } else {
    lcd.setCursor(0, 3);
    lcd.print("Auto start gen: OFF");
    Serial.println("auto start = 0");
  }
  lcd.noBlink();



  //******************************* Check power sources ********************************
  if (gridline_current_state == HIGH) {
    if (state != 0) {  // There's a change in state, such as from gen to grid
      turnOnBuzzer();
    }
    digitalWrite(gen_line_output, LOW);
    digitalWrite(solar_line_output, LOW);
    digitalWrite(grid_line_output, HIGH);
    lcd.setCursor(0, 0);  // set to line 1, char 0
    lcd.print("GRID is ON          ");
    Serial.println("grid is on");
    if (state == 1) {
      toggleGen(0);  // the gen was on and need to be turned off
    }
    state = 0;

  } else if (genline_current_state == HIGH) {
    if (state != 1) {  // There's a change in state, such as from pv to gen
      turnOnBuzzer();
    }
    digitalWrite(solar_line_output, LOW);
    digitalWrite(grid_line_output, LOW);
    digitalWrite(gen_on_switch, HIGH);
    digitalWrite(gen_line_output, HIGH);
    Serial.println("gen is on");
    lcd.setCursor(0, 0);  // set to line 1, char 0
    lcd.print("GEN is ON           ");
    state = 1;
  } else {
    if (state != 2) {  // There's a change in state, such as from grid to pv
      turnOnBuzzer();
    }
    digitalWrite(gen_line_output, LOW);
    digitalWrite(grid_line_output, LOW);
    digitalWrite(solar_line_output, HIGH);
    Serial.println("pv is on");
    lcd.setCursor(0, 0);  // set to line 1, char 0
    lcd.print("PV is ON            ");
    if (autoStartGen && state == 0) {  // check auto start gen and ensure that the previous state was grid
      toggleGen(1);
    }
    state = 2;
  }

  //********************************************* Check for bluetooth communication from the app ***********************************
  if (Serial.available() > 0) {        // Check if there is data coming
    bluetoothCommand = Serial.read();  // Read the message
    if (bluetoothCommand == '0') {     // user wants to turn off auto start gen
      autoStartGen = false;
    } else if (bluetoothCommand == '1') {  // user wants to turn on auto start gen
      autoStartGen = true;
    } else if (bluetoothCommand == '2') {  // user wants to turn on gen
      toggleGen(1);
    } else if (bluetoothCommand == '3') {  // user wants to turn off gen
      toggleGen(0);
    }
  }

  //******************************************* Check gen State ********************************************************
  checkGenState();
}
void turnOnBuzzer() {
  digitalWrite(buzzer, HIGH);
  delay(250);
  digitalWrite(buzzer, LOW);
}

void toggleGen(int toggle) {
  if (toggle == 0) {
    digitalWrite(gen_start_switch, LOW);
    digitalWrite(gen_on_switch, LOW);
    return;
  }

  if (digitalRead(grid_line_input) == HIGH) return;  // ensure that the grid isn't back on before trying to turn on gen
  if (digitalRead(gen_line_input) == HIGH) return;   // ensure that the gen isn't on before trying to turn on gen

  if (checkGenState()) {
    digitalWrite(gen_on_switch, HIGH);
    digitalWrite(gen_start_switch, HIGH);
    if (digitalRead(grid_line_input) == HIGH) return;  // ensure that the grid isn't back on before going in to delay
    delay(2000);                                       // delay 2 seconds to do the kick start
    digitalWrite(gen_start_switch, LOW);
    if (digitalRead(grid_line_input) == HIGH) return;  // ensure that the grid isn't back on before going in to delay
    delay(10000);                                      // delay for 10 seconds and allow to settle (started or not)
    if (digitalRead(gen_line_input) == LOW) {
      if (toggle <= 2) {
        toggleGen(toggle + 1);  // retry turning on gen
      } else {                  // after three trials and the gen refused to start, we can assume there's a problem
        toggleGen(0);           // turn off the relays
      }
    }
  }
}



bool checkGenState() {
  bool can_start_gen = true;

  //******************************Measur water level*************************************
  int waterLevelValue = analogRead(waterLevel);  // read water level
  if (waterLevelValue < 200) {
    Serial.println("water level = Low");
    can_start_gen = false;
  } else if (waterLevelValue < 400) {
    Serial.println("water level = Mid");
  } else {
    Serial.println("water level = High");
  }
  Serial.println(waterLevelValue);

  //********************************Measure Oil level***************************************
  // Clear the trigPin
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  // Sets the trigPin on HIGH state for 10 micro seconds
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  // Reads the echoPin, returns the sound wave travel time in microseconds
  long duration = pulseIn(echoPin, HIGH);
  // Calculating the distance
  int distance = duration * 0.034 / 2;
  if (distance > 10) {
    Serial.println("oil level = Low");
    can_start_gen = false;
  } else if (distance > 6) {
    Serial.println("oil level = Mid");
  } else {
    Serial.println("oil level = High");
  }
  Serial.println(distance);

  return can_start_gen;
}
